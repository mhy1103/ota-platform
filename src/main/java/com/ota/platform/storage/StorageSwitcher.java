package com.ota.platform.storage;

import com.ota.platform.audit.AuditService;
import com.ota.platform.common.ApiException;
import com.ota.platform.common.Hashes;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.install.InstallConfig;
import com.ota.platform.install.InstallService;
import com.ota.platform.installer.InstallerPackage;
import com.ota.platform.installer.InstallerRepository;
import com.ota.platform.installer.InstallerService;
import com.ota.platform.patch.Patch;
import com.ota.platform.patch.PatchRepository;
import com.ota.platform.release.Asset;
import com.ota.platform.release.AssetRepository;
import com.ota.platform.release.ReleaseFileRepository;
import com.ota.platform.release.ReleaseRepository;
import com.ota.platform.release.ReleaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 在系统配置里切换文件存储（本机 ⇄ S3 兼容对象存储）。先把新存储里没有的文件都复制过去，逐个核对 SHA-256；
 * 复制期间新上传的再补一遍，全部齐了才换过去，换完再补一次漏的。原来存储里的文件不删。
 * 原来的存储连不上（存储桶删了、密钥失效、网络不通）时没法复制：新存储里文件都在就直接换；
 * 缺文件的版本和安装包先告诉管理员，确认清空它们才换过去，不会换过去一堆下载不了的版本。
 */
@Service
public class StorageSwitcher {

    public record Form(String type, String endpoint, String region, String bucket, String accessKey, String secretKey,
                       boolean pathStyle, String prefix, String publicUrl) {}

    /**
     * state：idle / running / confirm / done / failed。step 是在做哪一步：check 检查原来的存储、copy 复制、
     * verify 原来的存储连不上时核对新存储里有哪些文件、clear 清空缺文件的版本再换。
     * confirm：原来的存储连不上，新存储里又缺文件，要管理员确认清空才换；error 是连不上的原因，note 说明会清空什么。
     * done：note 说明怎么换过来的（原来的存储连不上时），error 是换过去以后出的问题（配置没保存上、收尾出错），
     * missing 是没复制过去的文件（原存储里本来就没有，或者跟着清空的版本一起不要了）
     */
    public record Status(String state, String step, String target, int total, int done, int copied, long copiedBytes,
                         int missing, String error, String note, long finishedAt) {

        /** 管理员还得看一眼的：等着确认清空，或者换过去了但有要知道的事 */
        public boolean needsAttention(Duration within) {
            boolean notable = "confirm".equals(state) || "done".equals(state) && (note != null || error != null || missing > 0);
            return notable && System.currentTimeMillis() - finishedAt < within.toMillis();
        }
    }

    private static final Logger log = LoggerFactory.getLogger(StorageSwitcher.class);

    private final SwitchableBlobStore blobs;
    private final AssetRepository assets;
    private final PatchRepository patches;
    private final InstallerRepository installers;
    private final InstallerService installerService;
    private final ReleaseRepository releases;
    private final ReleaseFileRepository releaseFiles;
    private final ReleaseService releaseService;
    private final StorageService storage;
    private final InstallService install;
    private final OtaProperties props;
    private final AuditService audit;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "storage-switch");
        t.setDaemon(true);
        return t;
    });
    private volatile Status status = new Status("idle", null, null, 0, 0, 0, 0, 0, null, null, 0);
    /** 等着管理员确认清空的那次切换：确认后照原来的设置再来一次 */
    private volatile Form pending;

    public StorageSwitcher(SwitchableBlobStore blobs, AssetRepository assets, PatchRepository patches, InstallerRepository installers,
                           InstallerService installerService, ReleaseRepository releases, ReleaseFileRepository releaseFiles,
                           ReleaseService releaseService, StorageService storage, InstallService install, OtaProperties props,
                           AuditService audit) {
        this.blobs = blobs;
        this.assets = assets;
        this.patches = patches;
        this.installers = installers;
        this.installerService = installerService;
        this.releases = releases;
        this.releaseFiles = releaseFiles;
        this.releaseService = releaseService;
        this.storage = storage;
        this.install = install;
        this.props = props;
        this.audit = audit;
    }

    public Status status() {
        return status;
    }

    /** 启动参数、环境变量里设置了文件存储时不能在这里切换：重启后会变回去，换过去以后上传的文件就找不到了 */
    public boolean lockedByStartup() {
        return install.fromStartup("ota.storage.type") || install.fromStartup("ota.storage.s3.endpoint")
                || install.fromStartup("ota.storage.s3.bucket");
    }

    /** 试着往新存储写一个小文件、读回来、不带签名下载一次（App 就是这样下载的），再删掉 */
    public String test(Form f) {
        BlobStore target = build(f);
        probe(target);
        return "s3".equals(f.type()) ? "连接正常：能写入、读取、删除文件，App 也能从下载地址下载" : "本机存储可以正常读写";
    }

    public synchronized void start(Form f, String operator) {
        begin(f, false, operator);
    }

    /** 管理员确认清空新存储里缺文件的版本：照上次的设置再来一次，这次清空后换过去 */
    public synchronized void clearAndSwitch(String operator) {
        Form f = pending;
        if (f == null || !"confirm".equals(status.state())) {
            throw ApiException.badRequest("NOTHING_PENDING", "没有等着确认的切换，重新点「切换存储」");
        }
        begin(f, true, operator);
    }

    private void begin(Form f, boolean clear, String operator) {
        if ("running".equals(status.state())) throw ApiException.badRequest("BUSY", "正在切换，等这次完成再操作");
        if (lockedByStartup()) {
            throw ApiException.badRequest("LOCKED", "文件存储是用启动参数（--ota.storage.*）或环境变量设置的，在这里切换重启后会变回去。请先去掉这些启动参数");
        }
        boolean toS3 = "s3".equals(f.type());
        if (!toS3 && !props.getStorage().isS3()) throw ApiException.badRequest("SAME", "现在用的就是本机存储");
        BlobStore target = build(f);
        probe(target);
        String label = toS3 ? "对象存储 " + f.bucket().trim() : "本机存储";
        pending = null;
        status = new Status("running", "check", label, 0, 0, 0, 0, 0, null, null, 0);
        worker.submit(() -> run(f, target, label, operator, clear));
    }

    private void run(Form f, BlobStore target, String label, String operator, boolean clear) {
        BlobStore source = blobs.current();
        try {
            String down = unreachable(source, keys());
            if (down == null) copyAll(f, source, target, label, operator);
            else rescue(f, target, label, operator, down, clear);
        } catch (Exception e) {
            log.warn("切换文件存储失败：{}", e.toString());
            Status s = status;
            status = new Status("failed", s.step(), label, s.total(), s.done(), s.copied(), s.copiedBytes(), s.missing(), why(e), null,
                    System.currentTimeMillis());
        }
    }

    /** 原来的存储能读：新存储里没有的都复制过去，复制期间新传的再补一遍，齐了再换。原存储里本来就没有的文件跳过、记个数 */
    private void copyAll(Form f, BlobStore source, BlobStore target, String label, String operator) throws Exception {
        int copied = 0, missing = 0;
        long bytes = 0;
        for (int pass = 0; pass < 2; pass++) {
            List<String> keys = keys();
            missing = 0;
            for (int i = 0; i < keys.size(); i++) {
                status = new Status("running", "copy", label, keys.size(), i, copied, bytes, missing, null, null, 0);
                String sha = keys.get(i);
                if (!missingIn(target, sha)) continue;
                long n = copy(source, target, sha);
                if (n < 0) {
                    missing++;
                } else {
                    copied++;
                    bytes += n;
                }
            }
        }
        int total = status.total();
        String problem = swap(f, target);
        try {
            // 复制期间原存储里又传进来的：已经换过去了，补不上只记日志
            for (String sha : keys()) {
                try {
                    if (!missingIn(target, sha)) continue;
                    long n = copy(source, target, sha);
                    if (n >= 0) {
                        copied++;
                        bytes += n;
                    }
                } catch (Exception e) {
                    log.warn("换过去以后补复制 {} 失败：{}", sha.substring(0, 12), e.toString());
                }
            }
            String detail = "切换文件存储到" + label + "：复制 " + copied + " 个文件（" + human(bytes) + "）"
                    + (missing > 0 ? "，原存储里本来就缺 " + missing + " 个" : "");
            audit.logAs(operator, null, "STORAGE_SWITCH", detail);
            log.info(detail);
        } catch (Exception e) {
            problem = join(problem, "已经换过去了，但收尾时出错：" + why(e));
        }
        status = new Status("done", "copy", label, total, total, copied, bytes, missing, problem, null, System.currentTimeMillis());
    }

    /**
     * 原来的存储连不上：新存储里已经有的文件照用。版本、安装包的文件新存储里都有就直接换；
     * 有缺的先停下来让管理员确认（clear 为 true 是已经确认过），清空这些版本和安装包再换，不留下载不了的版本
     */
    private void rescue(Form f, BlobStore target, String label, String operator, String down, boolean clear) throws Exception {
        List<String> keys = keys();
        Set<String> absent = new LinkedHashSet<>();
        for (int i = 0; i < keys.size(); i++) {
            status = new Status("running", "verify", label, keys.size(), i, 0, 0, absent.size(), down, null, 0);
            if (missingIn(target, keys.get(i))) absent.add(keys.get(i));
        }
        Set<String> rels = new LinkedHashSet<>();
        for (List<String> part : chunks(absent)) rels.addAll(releaseFiles.storedReleasesUsing(part));
        List<InstallerPackage> pkgs = installers.findAll().stream().filter(i -> absent.contains(i.getSha256())).toList();
        int kept = (int) Math.max(0, releases.countByExternalBaseUrlIsNull() - rels.size());
        int external = (int) releases.countByExternalBaseUrlIsNotNull();
        boolean needClear = !rels.isEmpty() || !pkgs.isEmpty();
        if (needClear && !clear) {
            pending = f;
            String warning = warning(label, rels.size(), pkgs.size(), kept, external);
            log.warn("切换文件存储到{}：原来的存储连不上（{}），等管理员确认清空。{}", label, down, warning);
            status = new Status("confirm", "verify", label, keys.size(), keys.size(), 0, 0, absent.size(), down, warning,
                    System.currentTimeMillis());
            return;
        }
        status = new Status("running", "clear", label, keys.size(), keys.size(), 0, 0, absent.size(), down, null, 0);
        int cleared = releaseService.deleteForStorage(rels, operator);
        String problem = swap(f, target);
        String note = null;
        try {
            for (InstallerPackage p : pkgs) installerService.delete(p);
            // 补丁文件新存储里没有的作废：设备改下完整的启动包
            patches.deleteAll(patches.findAll().stream()
                    .filter(p -> Patch.READY.equals(p.getStatus()) && absent.contains(p.getPatchSha())).toList());
            StorageService.Cleaned c = needClear ? storage.clean(Duration.ofHours(24)) : null;
            note = needClear
                    ? "原来的存储连不上，清空了" + what(cleared, pkgs.size(), false) + "后换了过来"
                    + (kept > 0 ? "，" + kept + " 个文件齐全的版本留着" : "")
                    + (c != null && c.files() > 0 ? "；顺便清理了 " + c.files() + " 个用不上的文件" : "")
                    : "原来的存储连不上，版本和安装包的文件新存储里都有，直接换了过来";
            String detail = "切换文件存储到" + label + "：原来的存储连不上（" + down + "）"
                    + (needClear ? "，清空了" + what(cleared, pkgs.size(), false) : "，版本和安装包的文件新存储里都有");
            audit.logAs(operator, null, "STORAGE_SWITCH", detail);
            log.info(detail);
        } catch (Exception e) {
            problem = join(problem, "已经换过去了，但收尾时出错：" + why(e));
        }
        status = new Status("done", "clear", label, keys.size(), keys.size(), 0, 0, needClear ? absent.size() : 0, problem, note,
                System.currentTimeMillis());
    }

    /** 确认清空前给管理员看的：会清空什么、留着什么、设备会怎样 */
    private static String warning(String label, int releases, int installers, int kept, int external) {
        return "切换的话会清空" + what(releases, installers, kept == 0) + "，然后换到" + label + "。"
                + (kept > 0 ? "这些版本的文件新存储里没有；另外 " + kept + " 个版本新存储里文件齐全，留着。" : "")
                + (external > 0 ? "外部链接的 " + external + " 个版本不受影响。" : "")
                + "清空的版本渠道上不再下发，App 继续用手机上已经下载的，重新上传就行。"
                + "清空了不能恢复：如果只是密钥、权限或者网络的问题，先取消，修好了再切换";
    }

    /** 「 12 个版本和 2 个安装包」「所有版本（12 个）」，数字开头的前面带空格 */
    private static String what(int releases, int installers, boolean all) {
        String s = releases <= 0 ? "" : all ? "所有版本（" + releases + " 个）" : " " + releases + " 个版本";
        if (installers > 0) s += (s.isEmpty() ? " " : "和 ") + installers + " 个安装包";
        return s;
    }

    /** 换过去并记下配置。配置没保存上返回提示（重启后会变回去），不算切换失败：已经在用新存储了 */
    private String swap(Form f, BlobStore target) {
        blobs.swap(target);
        apply(f);
        try {
            install.saveStorage(config(f));
            return null;
        } catch (RuntimeException e) {
            log.warn("换了文件存储，但保存配置失败：{}", e.toString());
            return "已经换过去了，但配置没保存上（" + why(e) + "），重启后会变回原来的存储，请再切换一次";
        }
    }

    private static String join(String a, String b) {
        return a == null ? b : a + "。" + b;
    }

    /**
     * 原来的存储连不连得上：对象存储拿几个存着的文件试读，读得到、或者回答「没有这个文件」都算连得上，都不行返回原因。
     * 本机存储就是本机磁盘，不用试；一个文件都没有时也用不着它
     */
    private static String unreachable(BlobStore source, List<String> keys) {
        if (!(source instanceof S3BlobStore s3)) return null;
        String why = null;
        for (String sha : keys.subList(0, Math.min(3, keys.size()))) {
            try {
                s3.check(sha);
                return null;
            } catch (NoSuchFileException e) {
                return null;
            } catch (IOException e) {
                why = reason(e);
                // 连不上、超时这类网络问题，换个文件也一样
                if (!(e instanceof S3BlobStore.ReadFailed)) break;
            }
        }
        return why;
    }

    private static String reason(Exception e) {
        String msg = why(e);
        return msg.startsWith("读取失败：") ? msg.substring("读取失败：".length()) : msg;
    }

    /** 所有要搬的文件：版本文件、启动包补丁、安装包 */
    private List<String> keys() {
        Set<String> all = new LinkedHashSet<>();
        for (Asset a : assets.findAll()) all.add(a.getSha256());
        for (Patch p : patches.findAll()) if (Patch.READY.equals(p.getStatus()) && p.getPatchSha() != null) all.add(p.getPatchSha());
        for (InstallerPackage i : installers.findAll()) all.add(i.getSha256());
        return List.copyOf(all);
    }

    /** 复制一个文件，返回字节数；原存储里没有这个文件返回 -1。网络偶尔断一下重试两次，存储明确拒绝、内容对不上的不重试 */
    private long copy(BlobStore from, BlobStore to, String sha) throws Exception {
        for (int attempt = 1; ; attempt++) {
            try {
                return copyOnce(from, to, sha);
            } catch (NoSuchFileException e) {
                return -1;
            } catch (IOException e) {
                if (attempt >= 3 || e instanceof Mismatch || e instanceof S3BlobStore.ReadFailed r && r.definite()) throw e;
                Thread.sleep(1000L * attempt);
            }
        }
    }

    private long copyOnce(BlobStore from, BlobStore to, String sha) throws IOException {
        Path tmpDir = Files.createDirectories(Path.of(props.getDataDir(), "tmp"));
        Path tmp = Files.createTempFile(tmpDir, "move-", ".bin");
        try {
            MessageDigest md = Hashes.sha256();
            long n = 0;
            try (InputStream in = from.open(sha); OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                for (int r; (r = in.read(buf)) > 0; ) {
                    md.update(buf, 0, r);
                    out.write(buf, 0, r);
                    n += r;
                }
            }
            if (!HexFormat.of().formatHex(md.digest()).equalsIgnoreCase(sha)) throw new Mismatch(sha);
            to.put(sha, tmp);
            return n;
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** 读到的内容和哈希对不上 */
    private static final class Mismatch extends IOException {
        Mismatch(String sha) {
            super("文件 " + sha.substring(0, 12) + " 内容和哈希对不上，原存储里的文件可能损坏了");
        }
    }

    private static List<List<String>> chunks(Collection<String> all) {
        List<String> list = List.copyOf(all);
        List<List<String>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i += 500) out.add(list.subList(i, Math.min(list.size(), i + 500)));
        return out;
    }

    private static String why(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    /** HEAD 被拒（比如 AWS 没给 ListBucket 权限时，不存在的对象回 403）就当不在，直接传一份：按哈希命名，重复传没关系 */
    private static boolean missingIn(BlobStore store, String sha) {
        try {
            return !store.exists(sha);
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void probe(BlobStore target) {
        byte[] data = ("ota-storage-test " + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        Path tmp = null;
        String sha = null;
        boolean stored = false;
        try {
            sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
            tmp = Files.createTempFile(Files.createDirectories(Path.of(props.getDataDir(), "tmp")), "probe-", ".bin");
            Files.write(tmp, data);
            target.put(sha, tmp);
            stored = true;
            try (InputStream in = target.open(sha)) {
                if (!Arrays.equals(in.readAllBytes(), data)) throw new IOException("读回来的内容不一致");
            }
            // App 下载文件不带签名：存储桶不是公共读、CDN 没配好时，切过去所有设备都下载失败
            String problem = target instanceof S3BlobStore s3 ? s3.publicProblem(sha, data) : null;
            if (problem != null) {
                throw ApiException.badRequest("STORAGE_PRIVATE", "能写入、读取，但 App 下载不了：" + problem
                        + "。把存储桶设为「公共读」，或者配好 CDN 回源后把 CDN 地址填到「下载地址」，再测试");
            }
            target.delete(sha);
            stored = false;
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("STORAGE_FAILED", "连接失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            if (stored) {
                try {
                    target.delete(sha);
                } catch (Exception ignored) {
                    // 测试文件删不掉不影响结果
                }
            }
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 临时文件删不掉不影响结果
                }
            }
        }
    }

    private BlobStore build(Form f) {
        if (f == null || f.type() == null) throw ApiException.badRequest("BAD_STORAGE", "请选择存储方式");
        try {
            return StorageConfig.create("s3".equals(f.type()), props, s3(f));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_STORAGE", e.getMessage());
        } catch (IllegalStateException e) {
            throw ApiException.badRequest("BAD_STORAGE", "对象存储要填写服务地址、存储桶、Access Key 和 Secret Key");
        } catch (IOException e) {
            throw ApiException.badRequest("BAD_STORAGE", "本机存储目录不能用：" + e.getMessage());
        }
    }

    /** 表单里 Secret Key 留空时沿用现在的 */
    private OtaProperties.S3 s3(Form f) {
        OtaProperties.S3 cur = props.getStorage().getS3();
        OtaProperties.S3 s = new OtaProperties.S3();
        s.setEndpoint(S3Client.clean(f.endpoint()));
        s.setRegion(S3Client.clean(f.region()).isEmpty() ? "us-east-1" : S3Client.clean(f.region()));
        s.setBucket(S3Client.clean(f.bucket()));
        s.setAccessKey(S3Client.clean(f.accessKey()));
        s.setSecretKey(S3Client.clean(f.secretKey()).isEmpty() && props.getStorage().isS3() ? cur.getSecretKey() : S3Client.clean(f.secretKey()));
        s.setPathStyle(f.pathStyle());
        s.setPrefix(f.prefix() == null ? "ota/" : f.prefix().trim());
        s.setPublicUrl(S3Client.clean(f.publicUrl()).replaceAll("/+$", ""));
        return s;
    }

    private void apply(Form f) {
        if ("s3".equals(f.type())) props.getStorage().setS3(s3(f));
        props.getStorage().setType("s3".equals(f.type()) ? "s3" : "local");
    }

    private InstallConfig.Storage config(Form f) {
        InstallConfig.Storage c = new InstallConfig.Storage();
        if (!"s3".equals(f.type())) return c;
        OtaProperties.S3 s = props.getStorage().getS3();
        c.type = "s3";
        c.endpoint = s.getEndpoint();
        c.region = s.getRegion();
        c.bucket = s.getBucket();
        c.accessKey = s.getAccessKey();
        c.secretKey = s.getSecretKey();
        c.pathStyle = s.isPathStyle();
        c.prefix = s.getPrefix();
        c.publicUrl = s.getPublicUrl();
        return c;
    }

    static String human(long b) {
        if (b >= 1L << 30) return String.format("%.2f GB", b / (double) (1L << 30));
        if (b >= 1L << 20) return String.format("%.1f MB", b / 1048576.0);
        if (b >= 1024) return String.format("%.1f KB", b / 1024.0);
        return b + " B";
    }
}
