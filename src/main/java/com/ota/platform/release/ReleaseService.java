package com.ota.platform.release;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.platform.app.Channel;
import com.ota.platform.app.ChannelRepository;
import com.ota.platform.app.OtaApp;
import com.ota.platform.common.ApiException;
import com.ota.platform.common.Hashes;
import com.ota.platform.patch.PatchService;
import com.ota.platform.pkg.ExternalPackage;
import com.ota.platform.pkg.PackageFiles;
import com.ota.platform.storage.AssetService;
import com.ota.platform.storage.BlobStore;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class ReleaseService {

    private static final Pattern RUNTIME = Pattern.compile("[A-Za-z0-9._:+-]{1,64}");
    private static final Set<String> PLATFORMS = Set.of("ios", "android");

    private final ReleaseRepository releases;
    private final ReleaseFileRepository files;
    private final AssetRepository assets;
    private final DeploymentRepository deployments;
    private final ChannelRepository channels;
    private final BlobStore blobs;
    private final AssetService assetService;
    private final PatchService patches;
    private final ObjectMapper mapper;
    private final DeployEventRepository history;

    public ReleaseService(ReleaseRepository releases, ReleaseFileRepository files, AssetRepository assets,
                          DeploymentRepository deployments, ChannelRepository channels, BlobStore blobs,
                          AssetService assetService, PatchService patches, ObjectMapper mapper, DeployEventRepository history) {
        this.releases = releases;
        this.files = files;
        this.assets = assets;
        this.deployments = deployments;
        this.channels = channels;
        this.blobs = blobs;
        this.assetService = assetService;
        this.patches = patches;
        this.mapper = mapper;
        this.history = history;
    }

    /** 最近的发布记录（发布、调灰度、回滚、回退内置版本） */
    public List<DeployEvent> history(OtaApp app, int limit) {
        return history.recent(app.getId(), org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /** metadata.json 里列出的一个文件 */
    public record MetaFile(String platform, String path, String ext, boolean launch) {}

    /** 发布结果：republished 为 true 表示目标版本比线上版本旧，复制成了新版本再发布 */
    public record Deployed(Deployment deployment, Release release, boolean republished) {}

    /** 设备该拿到什么：一个版本、回退到内置版本的指令，或者什么都没有 */
    public record Resolution(Release release, Instant rollbackToEmbeddedAt) {
        public static final Resolution NONE = new Resolution(null, null);
    }

    // ==================== 登记版本 ====================

    /**
     * 登记一个版本。metadata 是 npx expo export 生成的 metadata.json，files 是「文件路径 → SHA-256」。
     * 引用到的每个文件都必须已经上传。
     */
    @Transactional
    public Release create(OtaApp app, String runtimeVersion, String message, boolean forceUpdate,
                          JsonNode metadata, JsonNode expoConfig, Map<String, String> fileHashes, String createdBy, String source) {
        String runtime = checkRuntime(runtimeVersion);
        if (metadata == null || !metadata.isObject()) throw ApiException.badRequest("BAD_METADATA", "缺少 metadata（metadata.json 的内容）");
        List<MetaFile> metaFiles = parseMetadata(metadata);

        Map<String, String> hashes = new HashMap<>();
        if (fileHashes != null) fileHashes.forEach((k, v) -> hashes.put(k, v == null ? null : v.trim().toLowerCase(Locale.ROOT)));
        List<String> problems = new ArrayList<>();
        Set<String> uniqueShas = new LinkedHashSet<>();
        for (MetaFile f : metaFiles) {
            String sha = hashes.get(f.path());
            if (sha == null) {
                problems.add(f.path() + " 没有提供哈希");
            } else if (!Hashes.isSha256Hex(sha)) {
                problems.add(f.path() + " 的哈希格式不对");
            } else if (!assets.existsById(sha) || !blobs.exists(sha)) {
                problems.add(f.path() + " 还没上传");
            } else {
                uniqueShas.add(sha);
            }
        }
        if (!problems.isEmpty()) {
            String head = String.join("；", problems.subList(0, Math.min(5, problems.size())));
            throw ApiException.badRequest("FILES_NOT_READY", "有 " + problems.size() + " 个文件不可用：" + head + (problems.size() > 5 ? " 等" : ""));
        }
        long total = assets.findAllById(uniqueShas).stream().mapToLong(Asset::getSize).sum();
        return save(app, runtime, message, forceUpdate, metadata, expoConfig, metaFiles, hashes, total, createdBy, source, null);
    }

    /** 外部链接版本：文件在 CDN 上，只登记哈希，App 直接从外部链接下载 */
    @Transactional
    public Release createExternal(OtaApp app, ExternalPackage.Fetched f, String message, boolean forceUpdate, String createdBy) {
        String runtime = checkRuntime(f.runtime() != null ? f.runtime() : "1.0.0");
        JsonNode metadata = readTree(f.metadata());
        JsonNode expoConfig = f.expoConfig() == null ? null : readTree(f.expoConfig());
        List<MetaFile> metaFiles = parseMetadata(metadata);
        Map<String, String> hashes = new HashMap<>();
        for (MetaFile mf : metaFiles) {
            PackageFiles.Digest d = f.files().get(mf.path());
            if (d == null) throw ApiException.badRequest("FILES_NOT_READY", mf.path() + " 没有读到");
            String sha = Hashes.hexOfBase64Url(d.hash());
            assetService.register(sha, d.key(), d.size());
            hashes.put(mf.path(), sha);
        }
        return save(app, runtime, message, forceUpdate, metadata, expoConfig, metaFiles, hashes, f.totalBytes(), createdBy,
                Release.SOURCE_EXTERNAL, f.baseUrl());
    }

    private Release save(OtaApp app, String runtime, String message, boolean forceUpdate, JsonNode metadata, JsonNode expoConfig,
                         List<MetaFile> metaFiles, Map<String, String> hashes, long total, String createdBy, String source,
                         String externalBaseUrl) {
        if (message != null && message.length() > 500) throw ApiException.badRequest("BAD_MESSAGE", "说明最多 500 字");
        Release release = new Release();
        release.setId(UUID.randomUUID().toString());
        fill(release, app, runtime, message, forceUpdate, metadata, expoConfig, metaFiles, total, createdBy, source, externalBaseUrl);
        releases.save(release);
        saveFiles(release.getId(), metaFiles, hashes);
        return release;
    }

    private void fill(Release r, OtaApp app, String runtime, String message, boolean forceUpdate, JsonNode metadata, JsonNode expoConfig,
                      List<MetaFile> metaFiles, long total, String createdBy, String source, String externalBaseUrl) {
        r.setAppId(app.getId());
        r.setRuntimeVersion(runtime);
        r.setMessage(message == null || message.isBlank() ? null : message.trim());
        r.setForceUpdate(forceUpdate);
        r.setPlatforms(String.join(",", new TreeSet<>(metaFiles.stream().map(MetaFile::platform).toList())));
        r.setMetadataJson(toJson(metadata));
        r.setExpoConfigJson(expoConfig == null || expoConfig.isNull() ? null : toJson(expoConfig));
        r.setTotalBytes(total);
        r.setCreatedBy(createdBy);
        r.setSource(source);
        r.setExternalBaseUrl(externalBaseUrl);
    }

    private void saveFiles(String releaseId, List<MetaFile> metaFiles, Map<String, String> hashes) {
        int order = 0;
        for (MetaFile f : metaFiles) {
            files.save(new ReleaseFile(releaseId, f.platform(), f.path(), hashes.get(f.path()), f.ext(), f.launch(), order++));
        }
    }

    /** 改说明和「强制更新」。已经装上这个版本的设备不会再下载，之后请求清单的设备拿到新值 */
    @Transactional
    public Release update(OtaApp app, String releaseId, String message, Boolean forceUpdate) {
        Release r = requireRelease(app, releaseId);
        if (message != null) {
            if (message.length() > 500) throw ApiException.badRequest("BAD_MESSAGE", "说明最多 500 字");
            r.setMessage(message.isBlank() ? null : message.trim());
        }
        if (forceUpdate != null) r.setForceUpdate(forceUpdate);
        return releases.save(r);
    }

    /** 复制一个版本（新的 ID 和时间、同样的文件）。App 只接受比当前版本新的版本，回滚要靠它 */
    @Transactional
    public Release republish(OtaApp app, Release source, String by) {
        Release r = new Release();
        r.setId(UUID.randomUUID().toString());
        r.setAppId(app.getId());
        r.setRuntimeVersion(source.getRuntimeVersion());
        r.setMessage(source.getMessage());
        r.setForceUpdate(source.isForceUpdate());
        r.setPlatforms(source.getPlatforms());
        r.setMetadataJson(source.getMetadataJson());
        r.setExpoConfigJson(source.getExpoConfigJson());
        r.setTotalBytes(source.getTotalBytes());
        r.setSource(Release.SOURCE_REPUBLISH);
        r.setExternalBaseUrl(source.getExternalBaseUrl());
        r.setRepublishedFrom(source.getRepublishedFrom() != null ? source.getRepublishedFrom() : source.getId());
        r.setCreatedBy(by);
        releases.save(r);
        for (ReleaseFile f : files.findByReleaseIdOrderBySortOrderAsc(source.getId())) {
            files.save(new ReleaseFile(r.getId(), f.getPlatform(), f.getPath(), f.getAssetSha256(), f.getExt(), f.isLaunch(), f.getSortOrder()));
        }
        return r;
    }

    // ==================== 发布、回滚 ====================

    /**
     * 发布到渠道：同一渠道、同一 Runtime 只有一个线上版本，原来的版本记为上一版本，用于灰度和回滚。
     * 要发布的版本不比线上版本新时，复制成新版本再发布，否则已经装了线上版本的设备不会换过去。
     */
    @Transactional
    public Deployed deploy(OtaApp app, Channel channel, String releaseId, int rolloutPercent, String by) {
        return deployAs(app, channel, releaseId, rolloutPercent, by, null);
    }

    /** action 为空时按情况记成「发布」或「调整灰度」 */
    private Deployed deployAs(OtaApp app, Channel channel, String releaseId, int rolloutPercent, String by, String action) {
        if (rolloutPercent < 1 || rolloutPercent > 100) throw ApiException.badRequest("BAD_ROLLOUT", "灰度比例要在 1 到 100 之间");
        Release release = requireRelease(app, releaseId);
        String runtime = release.getRuntimeVersion();
        Deployment d = deployments.findByChannelIdAndRuntimeVersion(channel.getId(), runtime)
                .orElseGet(() -> new Deployment(channel.getId(), runtime));
        Release current = d.getReleaseId() == null ? null : releases.findById(d.getReleaseId()).orElse(null);
        String mode = decide(d, current, release);
        // 线上已经是这份内容：当成同一个版本，只调灰度。再复制一份内容没变，却会让所有设备再「更新」一次
        if (PLAN_SAME.equals(mode)) release = current;
        boolean sameRelease = release.getId().equals(d.getReleaseId()) && !d.isRollbackToEmbedded();
        boolean republished = false;
        if (PLAN_COPY.equals(mode)) {
            release = republish(app, release, by);
            republished = true;
        }
        if (!release.getId().equals(d.getReleaseId())) {
            d.setPreviousReleaseId(d.getReleaseId());
            d.setReleaseId(release.getId());
        }
        d.setRolloutPercent(rolloutPercent);
        d.setRollbackToEmbedded(false);
        d.setRollbackAt(null);
        d.setUpdatedAt(Instant.now());
        d.setUpdatedBy(by);
        deployments.save(d);
        String kind = action != null ? action : sameRelease ? DeployEvent.ROLLOUT : DeployEvent.DEPLOY;
        history.save(new DeployEvent(app.getId(), channel.getName(), runtime, kind, release, rolloutPercent, by));
        schedulePatches(app, release, d.getPreviousReleaseId());
        return new Deployed(d, release, republished);
    }

    public static final String PLAN_NEW = "new";
    public static final String PLAN_SAME = "same";
    public static final String PLAN_COPY = "copy";

    /** 发布前先说清楚会怎样：mode 是 new（正常发布）、same（线上已经是这份内容，只调灰度）、copy（比线上旧，复制成新版本发布）；live 是现在的线上版本 */
    public record Plan(String mode, Release live) {}

    @Transactional(readOnly = true)
    public Plan plan(OtaApp app, Channel channel, String releaseId) {
        Release release = requireRelease(app, releaseId);
        Deployment d = deployments.findByChannelIdAndRuntimeVersion(channel.getId(), release.getRuntimeVersion()).orElse(null);
        Release current = d == null || d.getReleaseId() == null ? null : releases.findById(d.getReleaseId()).orElse(null);
        return new Plan(current == null ? PLAN_NEW : decide(d, current, release), current);
    }

    /**
     * 同一份内容（线上是它的副本、它是线上副本的原版、或者同一个 dist 分别上传了两次）不复制；
     * 要发布的比线上版本旧（创建时间不比线上晚）才复制成新版本：App 只装比自己正在用的更新的版本
     */
    private String decide(Deployment d, Release current, Release release) {
        if (current == null) return PLAN_NEW;
        if (!d.isRollbackToEmbedded() && (origin(current).equals(origin(release)) || sameContent(current, release))) return PLAN_SAME;
        // 刚回退过内置版本：已回退的设备只会加载 createdAt 比回退时刻新的版本。
        // 目标版本（含回滚到更新过的那个版本）createdAt 不晚于回退时刻时，复制成新版本刷新 createdAt，设备才会再次触发更新。
        if (d.isRollbackToEmbedded() && d.getRollbackAt() != null && !release.getCreatedAt().isAfter(d.getRollbackAt())) return PLAN_COPY;
        if (!release.getId().equals(current.getId()) && !release.getCreatedAt().isAfter(current.getCreatedAt())) return PLAN_COPY;
        return PLAN_NEW;
    }

    /** 对设备来说是同一个更新：文件（平台、路径、内容）、下载地址、强制更新、expoConfig 都一样 */
    private boolean sameContent(Release a, Release b) {
        if (!Objects.equals(a.getRuntimeVersion(), b.getRuntimeVersion()) || a.isForceUpdate() != b.isForceUpdate()
                || !Objects.equals(a.getExternalBaseUrl(), b.getExternalBaseUrl()) || !Objects.equals(a.getExpoConfigJson(), b.getExpoConfigJson())) {
            return false;
        }
        return fileSet(a).equals(fileSet(b));
    }

    private Set<String> fileSet(Release r) {
        Set<String> out = new HashSet<>();
        for (ReleaseFile f : files.findByReleaseIdOrderBySortOrderAsc(r.getId())) {
            out.add(f.getPlatform() + "|" + f.getPath() + "|" + f.getAssetSha256() + "|" + f.isLaunch());
        }
        return out;
    }

    /** 回滚到上一版本（复制成新版本发布，设备才会换回去） */
    @Transactional
    public Deployed rollback(OtaApp app, Channel channel, String runtimeVersion, String by) {
        return rollback(app, channel, runtimeVersion, null, by);
    }

    /**
     * 回滚到指定版本：to 为空时回到上一版本，也可以是更早的任意版本（完整 ID 或至少 6 位前缀）。
     * 只能选同一个 Runtime 的版本；选的就是现在线上的内容时拒绝。旧版本会复制成新版本发布，已经更新的设备也会换回去。
     */
    @Transactional
    public Deployed rollback(OtaApp app, Channel channel, String runtimeVersion, String to, String by) {
        Deployment d = requireDeployment(channel, runtimeVersion);
        Release target;
        if (to == null || to.isBlank()) {
            if (d.getPreviousReleaseId() == null) throw ApiException.badRequest("NO_PREVIOUS", "这个渠道没有上一版本可以回滚，请选择要回滚到的版本");
            target = requireRelease(app, d.getPreviousReleaseId());
        } else {
            target = resolve(app, to);
        }
        if (!target.getRuntimeVersion().equals(d.getRuntimeVersion())) {
            throw ApiException.badRequest("RUNTIME_MISMATCH", "版本 " + target.shortId() + " 的 Runtime 是 " + target.getRuntimeVersion()
                    + "，只能回滚到 Runtime " + d.getRuntimeVersion() + " 的版本");
        }
        if (!d.isRollbackToEmbedded() && d.getReleaseId() != null) {
            Release current = releases.findById(d.getReleaseId()).orElse(null);
            if (current != null && origin(current).equals(origin(target))) {
                throw ApiException.badRequest("SAME_RELEASE", "版本 " + target.shortId() + " 和现在的线上版本内容相同，不用回滚");
            }
        }
        return deployAs(app, channel, target.getId(), 100, by, DeployEvent.ROLLBACK);
    }

    /** 复制出来的版本算作原版本：内容相同 */
    public static String origin(Release r) {
        return r.getRepublishedFrom() != null ? r.getRepublishedFrom() : r.getId();
    }

    /** 按完整 ID 或至少 6 位的 ID 前缀找版本（命令行、接口里输短 ID 方便） */
    public Release resolve(OtaApp app, String idOrPrefix) {
        String v = idOrPrefix == null ? "" : idOrPrefix.trim().toLowerCase(java.util.Locale.ROOT);
        Optional<Release> exact = releases.findById(v).filter(r -> r.getAppId().equals(app.getId()));
        if (exact.isPresent()) return exact.get();
        if (v.length() < 6 || !v.matches("[0-9a-f-]+")) throw ApiException.notFound("RELEASE_NOT_FOUND", "版本 " + idOrPrefix + " 不存在");
        List<Release> hits = all(app).stream().filter(r -> r.getId().startsWith(v)).toList();
        if (hits.isEmpty()) throw ApiException.notFound("RELEASE_NOT_FOUND", "版本 " + idOrPrefix + " 不存在");
        if (hits.size() > 1) throw ApiException.badRequest("AMBIGUOUS_RELEASE", "有 " + hits.size() + " 个版本以 " + idOrPrefix + " 开头，请多输几位");
        return hits.get(0);
    }

    /** 让设备回到安装包内置的版本 */
    @Transactional
    public Deployment rollbackToEmbedded(OtaApp app, Channel channel, String runtimeVersion, String by) {
        String runtime = checkRuntime(runtimeVersion);
        Deployment d = deployments.findByChannelIdAndRuntimeVersion(channel.getId(), runtime)
                .orElseGet(() -> new Deployment(channel.getId(), runtime));
        d.setRollbackToEmbedded(true);
        d.setRollbackAt(Instant.now());
        d.setUpdatedAt(Instant.now());
        d.setUpdatedBy(by);
        Deployment saved = deployments.save(d);
        history.save(new DeployEvent(app.getId(), channel.getName(), runtime, DeployEvent.EMBEDDED, null, 0, by));
        return saved;
    }

    /** 设口令更新包的结果：changed 为 false 表示它本来就是这个渠道的口令更新包 */
    public record Activation(Deployment deployment, Release release, boolean changed) {}

    /**
     * 设口令更新包：这个渠道、这个版本的 Runtime 下，还在用安装包内置版本的设备（新装的、正在输入口令激活的）检查更新时拿它。
     * 不灰度；线上版本（常规更新包）、灰度、回滚、回退内置版本都不受影响。同一个版本可以既是常规更新包又是口令更新包。
     */
    @Transactional
    public Activation setActivation(OtaApp app, Channel channel, String releaseId, String by) {
        Release r = requireRelease(app, releaseId);
        String runtime = r.getRuntimeVersion();
        Deployment d = deployments.findByChannelIdAndRuntimeVersion(channel.getId(), runtime)
                .orElseGet(() -> new Deployment(channel.getId(), runtime));
        if (r.getId().equals(d.getActivationReleaseId())) return new Activation(d, r, false);
        d.setActivation(r.getId(), by);
        Deployment saved = deployments.save(d);
        history.save(new DeployEvent(app.getId(), channel.getName(), runtime, DeployEvent.ACTIVATION, r, 100, by));
        return new Activation(saved, r, true);
    }

    /** 取消口令更新包：还在用内置版本的设备和以前一样拿常规更新包（线上版本） */
    @Transactional
    public Deployment clearActivation(OtaApp app, Channel channel, String runtimeVersion, String by) {
        Deployment d = requireDeployment(channel, runtimeVersion);
        if (d.getActivationReleaseId() == null) {
            throw ApiException.badRequest("NO_ACTIVATION", channel.getName() + " 在 Runtime " + d.getRuntimeVersion() + " 下没有单独的口令更新包");
        }
        d.setActivation(null, by);
        Deployment saved = saveOrDrop(d);
        history.save(new DeployEvent(app.getId(), channel.getName(), d.getRuntimeVersion(), DeployEvent.ACTIVATION_OFF, null, 0, by));
        return saved;
    }

    /**
     * 渠道在这个 Runtime 下什么都不剩了（没有线上版本、没有口令更新包、也不在回退内置版本状态）就删掉这条记录，线上版本里不留空行。
     * 回退内置版本状态的留着：还没联网的设备回来时要收到回退指令，不然会一直用着删掉的版本
     */
    private Deployment saveOrDrop(Deployment d) {
        if (d.getReleaseId() == null && d.getActivationReleaseId() == null && !d.isRollbackToEmbedded()) {
            if (d.getId() != null) deployments.delete(d);
            return d;
        }
        return deployments.save(d);
    }

    /** 渠道在这个 Runtime 下单独设的口令更新包；没设时为空 */
    @Transactional(readOnly = true)
    public Optional<Release> activationRelease(OtaApp app, String channelName, String runtimeVersion) {
        return channels.findByAppIdAndName(app.getId(), channelName)
                .flatMap(c -> deployments.findByChannelIdAndRuntimeVersion(c.getId(), runtimeVersion))
                .map(Deployment::getActivationReleaseId)
                .flatMap(releases::findById);
    }

    /** 把这个版本设成了口令更新包的渠道发布 */
    public List<Deployment> activationSpots(String releaseId) {
        return releaseId == null ? List.of() : deployments.findByActivationReleaseId(releaseId);
    }

    /**
     * 删除版本：还有设备在拿的不能删（某个渠道的线上版本，或者灰度没到 100% 时其余设备拿的上一版本）。
     * 只是留着回滚的上一版本、回退到内置版本以后不再下发的版本可以删，渠道里对它的引用一起清掉，回滚时再选别的版本。
     * 文件按内容共用，不删。
     */
    @Transactional
    public Release delete(OtaApp app, String releaseId) {
        Release r = requireRelease(app, releaseId);
        String why = inUse(r.getId());
        if (why != null) throw ApiException.badRequest("RELEASE_IN_USE", why);
        for (Deployment d : deployments.findByReleaseIdOrPreviousReleaseId(r.getId(), r.getId())) {
            if (r.getId().equals(d.getPreviousReleaseId())) d.setPreviousReleaseId(null);
            if (r.getId().equals(d.getReleaseId())) d.setReleaseId(null);
            saveOrDrop(d);
        }
        files.deleteByReleaseId(r.getId());
        releases.delete(r);
        return r;
    }

    /**
     * 换文件存储时原来的存储连不上，管理员确认清空新存储里没有文件的版本：正在发布的也删，渠道里对它们的引用
     * （线上版本、灰度的上一版本、口令更新包）一起清掉。渠道没有版本可发时设备收到「没有更新」，继续用手机上已经下载的版本。
     */
    @Transactional
    public int deleteForStorage(Collection<String> releaseIds, String by) {
        Set<String> ids = new HashSet<>(releaseIds);
        if (ids.isEmpty()) return 0;
        for (Deployment d : deployments.findAll()) {
            boolean hit = false;
            if (ids.contains(d.getReleaseId())) {
                d.setReleaseId(null);
                hit = true;
            }
            if (ids.contains(d.getPreviousReleaseId())) {
                d.setPreviousReleaseId(null);
                hit = true;
            }
            if (ids.contains(d.getActivationReleaseId())) {
                d.setActivation(null, by);
                hit = true;
            }
            if (hit) saveOrDrop(d);
        }
        List<Release> rels = releases.findAllById(ids);
        for (Release r : rels) files.deleteByReleaseId(r.getId());
        releases.deleteAll(rels);
        return rels.size();
    }

    /** 还有设备在拿这个版本时返回原因（不能删），否则返回 null */
    public String inUse(String releaseId) {
        for (Deployment d : deployments.findByReleaseIdOrPreviousReleaseId(releaseId, releaseId)) {
            if (d.isRollbackToEmbedded()) continue;
            String channel = channels.findById(d.getChannelId()).map(Channel::getName).orElse("渠道");
            if (releaseId.equals(d.getReleaseId())) return "这是 " + channel + " 的线上版本，不能删除";
            if (releaseId.equals(d.getPreviousReleaseId()) && d.getRolloutPercent() < 100) {
                return channel + " 灰度 " + d.getRolloutPercent() + "%，其余 " + (100 - d.getRolloutPercent()) + "% 的设备还在用这个版本，不能删除";
            }
        }
        for (Deployment d : deployments.findByActivationReleaseId(releaseId)) {
            String channel = channels.findById(d.getChannelId()).map(Channel::getName).orElse("渠道");
            return "这是 " + channel + " 的口令更新包，输入口令激活的设备在下载它，不能删除。先换一个或取消口令更新包";
        }
        return null;
    }

    private void schedulePatches(OtaApp app, Release target, String previousId) {
        if (target.isExternal() || !app.isPatchEnabled()) return;
        Runnable task = () -> patches.scheduleFor(target, previousId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    // ==================== 下发 ====================

    /**
     * 选出这台设备该拿的版本。灰度中的设备按设备 ID 稳定分桶：同一台设备每次结果一样；
     * 没带设备 ID 的请求不进灰度，继续拿上一版本。
     */
    public Resolution resolve(OtaApp app, String channelName, String runtimeVersion, String clientId) {
        return resolve(app, channelName, runtimeVersion, clientId, false);
    }

    /**
     * fresh：设备还在用安装包内置的版本（新装的、正在输入口令激活的）。App 输入口令后要马上下载到一个版本，
     * 拿不到就激活失败，所以这样的设备没进灰度时拿上一版本，没有上一版本（或者上一版本已经不在了）就拿线上版本，
     * 灰度不会让口令激活失败。
     */
    @Transactional(readOnly = true)
    public Resolution resolve(OtaApp app, String channelName, String runtimeVersion, String clientId, boolean fresh) {
        return resolve(app, channelName, runtimeVersion, clientId, fresh, false);
    }

    /** first：灰度白名单里的设备，灰度没到 100% 时也算在灰度里 */
    @Transactional(readOnly = true)
    public Resolution resolve(OtaApp app, String channelName, String runtimeVersion, String clientId, boolean fresh, boolean first) {
        Optional<Channel> channel = channels.findByAppIdAndName(app.getId(), channelName);
        if (channel.isEmpty()) return Resolution.NONE;
        Optional<Deployment> deployment = deployments.findByChannelIdAndRuntimeVersion(channel.get().getId(), runtimeVersion);
        if (deployment.isEmpty()) return Resolution.NONE;
        Deployment d = deployment.get();
        if (d.isRollbackToEmbedded()) return new Resolution(null, d.getRollbackAt());
        String id = d.getReleaseId();
        if (id == null) return Resolution.NONE;
        if (d.getRolloutPercent() < 100) {
            boolean inRollout = first || clientId != null && !clientId.isBlank() && bucket(clientId, d.getReleaseId()) < d.getRolloutPercent();
            if (!inRollout) {
                String prev = d.getPreviousReleaseId();
                boolean prevThere = prev != null && releases.existsById(prev);
                if (prevThere || !fresh) id = prev;
            }
        }
        return id == null ? Resolution.NONE : new Resolution(releases.findById(id).orElse(null), null);
    }

    /** 这个版本正在当线上版本的渠道（回退到内置版本的不算） */
    public record LiveSpot(Channel channel, Deployment deployment) {}

    @Transactional(readOnly = true)
    public List<LiveSpot> liveSpots(String releaseId) {
        List<LiveSpot> out = new ArrayList<>();
        for (Deployment d : deployments.findByReleaseIdOrPreviousReleaseId(releaseId, releaseId)) {
            if (d.isRollbackToEmbedded() || !releaseId.equals(d.getReleaseId())) continue;
            channels.findById(d.getChannelId()).ifPresent(c -> out.add(new LiveSpot(c, d)));
        }
        return out;
    }

    /** 渠道在这个 Runtime 下的线上版本（不管灰度）；回退到内置版本时没有 */
    @Transactional(readOnly = true)
    public Optional<Release> liveRelease(OtaApp app, String channelName, String runtimeVersion) {
        return channels.findByAppIdAndName(app.getId(), channelName)
                .flatMap(c -> deployments.findByChannelIdAndRuntimeVersion(c.getId(), runtimeVersion))
                .filter(d -> !d.isRollbackToEmbedded() && d.getReleaseId() != null)
                .flatMap(d -> releases.findById(d.getReleaseId()));
    }

    // ==================== 查询 ====================

    public List<ReleaseFile> files(String releaseId, String platform) {
        return files.findByReleaseIdAndPlatformOrderBySortOrderAsc(releaseId, platform);
    }

    public List<ReleaseFile> allFiles(String releaseId) {
        return files.findByReleaseIdOrderBySortOrderAsc(releaseId);
    }

    public List<Release> recent(OtaApp app, int limit) {
        return releases.findByAppIdOrderByCreatedAtDesc(app.getId(), PageRequest.of(0, Math.max(1, Math.min(limit, 200))));
    }

    public List<Release> all(OtaApp app) {
        return releases.findByAppIdOrderByCreatedAtDesc(app.getId());
    }

    public Optional<Release> find(String releaseId) {
        return releaseId == null ? Optional.empty() : releases.findById(releaseId);
    }

    public Release requireRelease(OtaApp app, String releaseId) {
        return releases.findById(releaseId == null ? "" : releaseId.trim())
                .filter(r -> r.getAppId().equals(app.getId()))
                .orElseThrow(() -> ApiException.notFound("RELEASE_NOT_FOUND", "版本 " + releaseId + " 不存在"));
    }

    public List<Deployment> deployments(Channel channel) {
        return deployments.findByChannelIdOrderByRuntimeVersionAsc(channel.getId());
    }

    public Deployment requireDeployment(Channel channel, String runtimeVersion) {
        return deployments.findByChannelIdAndRuntimeVersion(channel.getId(), runtimeVersion == null ? "" : runtimeVersion.trim())
                .orElseThrow(() -> ApiException.notFound("DEPLOYMENT_NOT_FOUND", "渠道 " + channel.getName() + " 在 Runtime " + runtimeVersion + " 下还没有发布过版本"));
    }

    /** 0–99，同一设备、同一版本结果固定；换了版本会重新分桶 */
    public static int bucket(String clientId, String releaseId) {
        byte[] h = Hashes.sha256().digest((clientId.trim().toLowerCase(Locale.ROOT) + ":" + releaseId).getBytes(StandardCharsets.UTF_8));
        return Math.floorMod(ByteBuffer.wrap(h, 0, 4).getInt(), 100);
    }

    public static String checkRuntime(String runtimeVersion) {
        String runtime = runtimeVersion == null ? "" : runtimeVersion.trim();
        if (!RUNTIME.matcher(runtime).matches() || runtime.matches("\\.+")) {
            throw ApiException.badRequest("BAD_RUNTIME", "runtimeVersion 只能用字母、数字和 . _ : + -，最多 64 位");
        }
        return runtime;
    }

    /** 支持 fileMetadata 的对象形式（{ios: {...}}）和数组形式（[{platform: "ios", ...}]），只取 ios 和 android */
    public List<MetaFile> parseMetadata(JsonNode metadata) {
        JsonNode fm = metadata.get("fileMetadata");
        Map<String, JsonNode> platforms = new LinkedHashMap<>();
        if (fm != null && fm.isObject()) {
            fm.fields().forEachRemaining(e -> platforms.put(e.getKey(), e.getValue()));
        } else if (fm != null && fm.isArray()) {
            fm.forEach(n -> platforms.put(n.path("platform").asText(""), n));
        }
        List<MetaFile> out = new ArrayList<>();
        for (Map.Entry<String, JsonNode> e : platforms.entrySet()) {
            String platform = e.getKey();
            if (!PLATFORMS.contains(platform)) continue;
            String bundle = e.getValue().path("bundle").asText("");
            if (bundle.isBlank()) throw ApiException.badRequest("BAD_METADATA", "metadata.json 里 " + platform + " 没有启动包");
            out.add(new MetaFile(platform, checkPath(bundle), null, true));
            for (JsonNode a : e.getValue().path("assets")) {
                String path = a.path("path").asText("");
                if (path.isBlank()) continue;
                String ext = a.path("ext").asText("").replaceAll("^\\.", "");
                if (ext.length() > 16) throw ApiException.badRequest("BAD_METADATA", "扩展名太长：" + ext);
                out.add(new MetaFile(platform, checkPath(path), ext, false));
            }
        }
        if (out.isEmpty()) throw ApiException.badRequest("BAD_METADATA", "metadata.json 里没有 ios 或 android 的启动包");
        return out;
    }

    private static String checkPath(String path) {
        boolean bad = path.length() > 500 || path.startsWith("/") || path.contains("\\")
                || Arrays.asList(path.split("/")).contains("..");
        if (bad) throw ApiException.badRequest("BAD_METADATA", "metadata.json 里有非法路径：" + path);
        return path;
    }

    private JsonNode readTree(byte[] json) {
        try {
            return mapper.readTree(json);
        } catch (IOException e) {
            throw ApiException.badRequest("BAD_JSON", "JSON 无法解析");
        }
    }

    private String toJson(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw ApiException.badRequest("BAD_JSON", "JSON 无法序列化");
        }
    }
}
