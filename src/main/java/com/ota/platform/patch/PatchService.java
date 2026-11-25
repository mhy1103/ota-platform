package com.ota.platform.patch;

import com.ota.platform.config.OtaProperties;
import com.ota.platform.release.Asset;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseFile;
import com.ota.platform.release.ReleaseFileRepository;
import com.ota.platform.release.ReleaseRepository;
import com.ota.platform.storage.AssetService;
import com.ota.platform.storage.BlobStore;
import io.sigpipe.jbsdiff.Diff;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 启动包增量更新。每次发布后，为同一 App、同一 Runtime 的最近几个版本生成到新版本的 bsdiff 补丁（BSDIFF40 格式，
 * 和 expo-updates 内置的 bspatch 一致）。设备下载启动包时带上当前版本 ID，有现成补丁就只下补丁。
 */
@Service
public class PatchService {

    private static final Logger log = LoggerFactory.getLogger(PatchService.class);
    /** bsdiff 的后缀排序是递归的，默认线程栈在部分输入上会溢出 */
    private static final long DIFF_STACK_BYTES = 256L * 1024 * 1024;
    /** 退化的输入（比如大段重复内容）会算得非常慢，超时就放弃，设备改下完整文件 */
    private static final long DIFF_TIMEOUT_MS = 120_000;
    private static final long MAX_FILE_BYTES = 64L * 1024 * 1024;

    private final PatchRepository patches;
    private final ReleaseRepository releases;
    private final ReleaseFileRepository files;
    private final BlobStore blobs;
    private final AssetService assetService;
    private final OtaProperties props;
    /** bsdiff 吃 CPU 和内存，一次只算一个 */
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "patch-generator");
        t.setDaemon(true);
        return t;
    });
    private final Set<String> inflight = ConcurrentHashMap.newKeySet();

    public PatchService(PatchRepository patches, ReleaseRepository releases, ReleaseFileRepository files,
                        BlobStore blobs, AssetService assetService, OtaProperties props) {
        this.patches = patches;
        this.releases = releases;
        this.files = files;
        this.blobs = blobs;
        this.assetService = assetService;
        this.props = props;
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }

    /** target 发布后调用：基础版本是渠道的上一版本，加上同一 Runtime 最近的几个版本 */
    public void scheduleFor(Release target, String previousReleaseId) {
        if (!props.getPatch().isEnabled() || target.isExternal()) return;
        int limit = Math.max(0, props.getPatch().getBases());
        LinkedHashSet<String> baseIds = new LinkedHashSet<>();
        if (previousReleaseId != null) baseIds.add(previousReleaseId);
        for (Release r : releases.findByAppIdAndRuntimeVersionAndCreatedAtBeforeOrderByCreatedAtDesc(
                target.getAppId(), target.getRuntimeVersion(), target.getCreatedAt(), PageRequest.of(0, limit + 3))) {
            if (baseIds.size() >= limit + (previousReleaseId != null ? 1 : 0)) break;
            if (!r.isExternal()) baseIds.add(r.getId());
        }
        baseIds.remove(target.getId());
        for (String platform : target.getPlatforms().split(",")) {
            Optional<String> targetSha = launchSha(target.getId(), platform);
            if (targetSha.isEmpty()) continue;
            Set<String> seen = new HashSet<>();
            for (String baseId : baseIds) {
                Release base = releases.findById(baseId).orElse(null);
                if (base == null || base.isExternal()) continue;
                launchSha(baseId, platform)
                        .filter(sha -> !sha.equals(targetSha.get()) && seen.add(sha))
                        .ifPresent(baseSha -> submit(baseSha, targetSha.get()));
            }
        }
    }

    public Optional<String> launchSha(String releaseId, String platform) {
        return files.findFirstByReleaseIdAndPlatformAndLaunchTrue(releaseId, platform).map(ReleaseFile::getAssetSha256);
    }

    /** 已经生成好、可以下发的补丁 */
    public Optional<Patch> ready(String baseSha, String targetSha) {
        return patches.findByBaseShaAndTargetSha(baseSha, targetSha).filter(p -> Patch.READY.equals(p.getStatus()));
    }

    public List<Patch> forTargets(Collection<String> targetShas) {
        return targetShas.isEmpty() ? List.of() : patches.findByTargetShaIn(targetShas);
    }

    void submit(String baseSha, String targetSha) {
        if (patches.findByBaseShaAndTargetSha(baseSha, targetSha).isPresent()) return;
        String key = baseSha + ">" + targetSha;
        if (!inflight.add(key)) return;
        pool.submit(() -> {
            try {
                generate(baseSha, targetSha);
            } finally {
                inflight.remove(key);
            }
        });
    }

    private void generate(String baseSha, String targetSha) {
        long t0 = System.currentTimeMillis();
        Patch p = new Patch(baseSha, targetSha);
        try {
            byte[] base = readAll(baseSha);
            byte[] target = readAll(targetSha);
            p.setTargetSize(target.length);
            if (base.length > MAX_FILE_BYTES || target.length > MAX_FILE_BYTES) {
                throw new IllegalStateException("启动包超过 " + MAX_FILE_BYTES / 1024 / 1024 + " MB，不生成补丁");
            }
            byte[] patch = diff(base, target);
            p.setPatchSize(patch.length);
            if (patch.length > target.length * props.getPatch().getMaxRatio()) {
                p.setStatus(Patch.USELESS);
            } else {
                Path tmp = Files.createTempFile(assetService.tmpDir(), "patch-", ".bsdiff");
                try {
                    Files.write(tmp, patch);
                    Asset stored = assetService.store(tmp);
                    p.setPatchSha(stored.getSha256());
                } finally {
                    Files.deleteIfExists(tmp);
                }
                p.setStatus(Patch.READY);
            }
        } catch (Throwable e) {
            p.setStatus(Patch.FAILED);
            p.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("生成补丁失败 {} → {}：{}", baseSha.substring(0, 12), targetSha.substring(0, 12), e.toString());
        }
        p.setTookMs(System.currentTimeMillis() - t0);
        try {
            patches.save(p);
            log.info("补丁 {} → {}：{}，{} → {} 字节，用时 {} ms", baseSha.substring(0, 12), targetSha.substring(0, 12),
                    p.getStatus(), p.getTargetSize(), p.getPatchSize(), p.getTookMs());
        } catch (DataIntegrityViolationException ignored) {
            // 并发生成了同一个补丁，保留先写入的
        }
    }

    /** 在栈足够大的独立线程里算补丁，超时放弃（那个线程算完自己结束，结果丢弃） */
    private static byte[] diff(byte[] base, byte[] target) throws Exception {
        byte[][] result = new byte[1][];
        Throwable[] error = new Throwable[1];
        Thread worker = new Thread(null, () -> {
            try {
                ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(1024, target.length / 8));
                Diff.diff(base, target, out);
                result[0] = out.toByteArray();
            } catch (Throwable e) {
                error[0] = e;
            }
        }, "bsdiff", DIFF_STACK_BYTES);
        worker.setDaemon(true);
        worker.start();
        worker.join(DIFF_TIMEOUT_MS);
        if (worker.isAlive()) throw new IllegalStateException("计算补丁超过 " + DIFF_TIMEOUT_MS / 1000 + " 秒，放弃");
        if (error[0] != null) throw new IllegalStateException(error[0].getClass().getSimpleName() + ": " + error[0].getMessage());
        return result[0];
    }

    private byte[] readAll(String sha) throws Exception {
        try (InputStream in = blobs.open(sha)) {
            return in.readAllBytes();
        }
    }
}
