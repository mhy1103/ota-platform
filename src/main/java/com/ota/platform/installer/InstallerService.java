package com.ota.platform.installer;

import com.ota.platform.app.AppService;
import com.ota.platform.app.Channel;
import com.ota.platform.app.OtaApp;
import com.ota.platform.common.ApiException;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.install.InstallConfig;
import com.ota.platform.install.InstallService;
import com.ota.platform.release.AssetRepository;
import com.ota.platform.release.Deployment;
import com.ota.platform.release.DeploymentRepository;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseRepository;
import com.ota.platform.signing.PemSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.platform.storage.BlobStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安装包（可选）：上传 APK / IPA，读出里面 expo-updates 的配置，检查这个安装包能不能收到这个 App 的更新：
 * 更新地址、运行版本、渠道、签名证书对不对得上。校验版本时也会用同一套检查。
 */
@Service
public class InstallerService {

    public record Check(String label, String state, String text) {}

    private static final Logger log = LoggerFactory.getLogger(InstallerService.class);
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> ORDER = List.of("ok", "skip", "warn", "error");

    private final InstallerRepository repo;
    private final BlobStore blobs;
    private final AssetRepository assets;
    private final ReleaseRepository releases;
    private final DeploymentRepository deployments;
    private final AppService apps;
    private final InstallService install;
    private final OtaProperties props;

    public InstallerService(InstallerRepository repo, BlobStore blobs, AssetRepository assets, ReleaseRepository releases,
                            DeploymentRepository deployments, AppService apps, InstallService install, OtaProperties props) {
        this.repo = repo;
        this.blobs = blobs;
        this.assets = assets;
        this.releases = releases;
        this.deployments = deployments;
        this.apps = apps;
        this.install = install;
        this.props = props;
    }

    public List<InstallerPackage> list(OtaApp app) {
        return repo.findByAppIdOrderByUploadedAtDesc(app.getId());
    }

    public Optional<InstallerPackage> find(OtaApp app, String id) {
        return repo.findById(id).filter(p -> p.getAppId().equals(app.getId()));
    }

    public InstallerPackage upload(OtaApp app, MultipartFile file, String note, String by) throws IOException {
        if (file == null || file.isEmpty()) throw ApiException.badRequest("NO_FILE", "请选择安装包");
        String name = file.getOriginalFilename() == null ? "" : Path.of(file.getOriginalFilename()).getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).matches(".+\\.(apk|ipa)")) throw ApiException.badRequest("BAD_FILE", "只支持 .apk 和 .ipa 文件");
        Path dir = Files.createDirectories(Path.of(props.getDataDir(), "tmp"));
        Path tmp = Files.createTempFile(dir, "installer-", name.toLowerCase(Locale.ROOT).endsWith(".ipa") ? ".ipa" : ".apk");
        try {
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return store(app, tmp, name, note, by);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** 分片传完的文件（见 ChunkedUploads）；文件由调用方删除 */
    public InstallerPackage store(OtaApp app, Path file, String name, String note, String by) throws IOException {
        if (!name.toLowerCase(Locale.ROOT).matches(".+\\.(apk|ipa)")) throw ApiException.badRequest("BAD_FILE", "只支持 .apk 和 .ipa 文件");
        String sha;
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[64 * 1024];
            for (int r; (r = in.read(buf)) > 0; ) md.update(buf, 0, r);
            sha = HexFormat.of().formatHex(md.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        InstallerParser.Info info;
        try {
            info = InstallerParser.parse(file);
        } catch (IOException e) {
            throw ApiException.badRequest("BAD_FILE", e.getMessage());
        }
        long size = Files.size(file);
        if (!blobs.exists(sha)) blobs.put(sha, file);
        InstallerPackage p = new InstallerPackage();
        p.setId(UUID.randomUUID().toString());
        p.setAppId(app.getId());
        p.setFileName(cut(name, 255));
        p.setSize(size);
        p.setSha256(sha);
        fill(p, info);
        p.setNote(note == null || note.isBlank() ? null : cut(note.trim(), 500));
        p.setUploadedBy(by);
        p.setUploadedAt(Instant.now());
        return repo.save(p);
    }

    private static void fill(InstallerPackage p, InstallerParser.Info info) {
        p.setPlatform(info.platform());
        p.setBundleId(cut(info.bundleId(), 255));
        p.setVersionName(cut(info.versionName(), 100));
        p.setBuildNumber(cut(info.buildNumber(), 50));
        p.setRuntimeVersion(cut(info.runtimeVersion(), 100));
        p.setUpdateUrl(cut(info.updateUrl(), 500));
        p.setChannel(cut(info.channel(), 100));
        p.setCertificatePem(cut(info.certificatePem(), 8000));
        p.setKeyId(cut(info.keyId(), 100));
        p.setEmbeddedUpdateId(cut(info.embeddedUpdateId(), 100));
        p.setSdkVersion(cut(info.sdkVersion(), 30));
        p.setUpdatesEnabled(info.updatesEnabled());
        p.setWarnings(info.warnings().isEmpty() ? null : cut(String.join("\n", info.warnings()), 2000));
        p.setExtraJson(cut(info.extraJson(), 8000));
    }

    /** 本次运行里已经尝试重读过的安装包，读不了的不反复读 */
    private final Set<String> reread = ConcurrentHashMap.newKeySet();

    /** 旧版本读出来的记录（缺检查时机、内置包打包时间等）：从存着的安装包重新读一遍再检查 */
    private void fresh(InstallerPackage p) {
        if (extra(p).path("parser").asInt(0) >= InstallerParser.VERSION || !reread.add(p.getId())) return;
        Path tmp = null;
        try {
            Path file = blobs.localPath(p.getSha256());
            if (file == null || !Files.isRegularFile(file)) {
                tmp = Files.createTempFile(Files.createDirectories(Path.of(props.getDataDir(), "tmp")), "installer-", "ios".equals(p.getPlatform()) ? ".ipa" : ".apk");
                try (InputStream in = blobs.open(p.getSha256())) {
                    Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                file = tmp;
            }
            fill(p, InstallerParser.parse(file));
            repo.save(p);
        } catch (IOException | RuntimeException e) {
            log.warn("重新读取安装包 {} 失败：{}", p.getFileName(), e.toString());
        } finally {
            if (tmp != null) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {
                    // 临时目录下次清理
                }
            }
        }
    }

    public void delete(InstallerPackage p) {
        repo.delete(p);
        if (!repo.existsBySha256AndIdNot(p.getSha256(), p.getId()) && !assets.existsById(p.getSha256())) {
            try {
                blobs.delete(p.getSha256());
            } catch (IOException e) {
                log.warn("删除安装包文件 {} 失败：{}", p.getSha256(), e.toString());
            }
        }
    }

    public InputStream open(InstallerPackage p) throws IOException {
        return blobs.open(p.getSha256());
    }

    /** 这个安装包能不能收到这个 App 的更新 */
    public List<Check> checks(OtaApp app, InstallerPackage p) {
        fresh(p);
        List<Check> out = new ArrayList<>();
        if (Boolean.FALSE.equals(p.getUpdatesEnabled())) out.add(new Check("更新开关", "error", "安装包里关闭了 expo-updates，收不到任何更新"));
        out.add(urlCheck(app, p));
        out.add(runtimeCheck(app, p));
        out.add(channelCheck(app, p));
        out.add(signingCheck(app, p));
        out.add(freshnessCheck(app, p));
        out.add(checkOnLaunchCheck(p));
        out.add(transportCheck(p));
        out.add(embeddedCheck(p));
        return out;
    }

    public static String level(List<Check> checks) {
        String worst = "ok";
        for (Check c : checks) if (ORDER.indexOf(c.state()) > ORDER.indexOf(worst)) worst = c.state();
        return worst;
    }

    /** 校验版本时一起检查：同一个 Runtime、同平台的安装包 */
    public List<InstallerPackage> forRelease(OtaApp app, Release r) {
        List<String> platforms = r.getPlatforms() == null ? List.of() : Arrays.stream(r.getPlatforms().split("[,，\\s]+")).map(String::trim).toList();
        return repo.findByAppIdAndRuntimeVersion(app.getId(), r.getRuntimeVersion()).stream()
                .filter(p -> platforms.isEmpty() || platforms.contains(p.getPlatform())).toList();
    }

    public static String title(InstallerPackage p) {
        String plat = "ios".equals(p.getPlatform()) ? "iOS" : "Android";
        String ver = p.getVersionName() == null ? "" : " " + p.getVersionName() + (p.getBuildNumber() == null ? "" : "（" + p.getBuildNumber() + "）");
        return plat + ver;
    }

    private Check urlCheck(OtaApp app, InstallerPackage p) {
        String url = p.getUpdateUrl();
        String expect = props.baseUrl() + "/m/" + app.getSlug();
        if (url == null || url.isBlank()) return new Check("更新地址", "error", "没读到更新地址（EXUpdatesURL / EXPO_UPDATE_URL），安装包不会来检查更新");
        String norm = url.trim().replaceAll("/+$", "");
        if (norm.equalsIgnoreCase(expect)) return new Check("更新地址", "ok", "指向这个 App：" + norm);
        URI u;
        try {
            u = URI.create(norm);
        } catch (IllegalArgumentException e) {
            return new Check("更新地址", "error", "更新地址格式不对：" + norm);
        }
        String path = u.getPath() == null ? "" : u.getPath().replaceAll("/+$", "");
        boolean mine = path.equals("/m/" + app.getSlug());
        if (!mine) return new Check("更新地址", "error", "指向 " + norm + "，不是这个 App 的更新地址（应该是 " + expect + "）");
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        if (hostIsOurs(host)) return new Check("更新地址", "ok", "指向这个 App：" + norm);
        return new Check("更新地址", "warn", "路径对，但域名 " + host + " 不是现在的接口地址，也不在绑定的接口域名里，确认它能访问到本服务器");
    }

    private boolean hostIsOurs(String host) {
        try {
            String base = URI.create(props.baseUrl()).getHost();
            if (base != null && base.equalsIgnoreCase(host)) return true;
        } catch (IllegalArgumentException ignored) {
            // 对外地址写错时只看绑定的域名
        }
        for (InstallConfig.Domain d : install.snapshot().domains) {
            if (!d.api || d.host == null) continue;
            String h = d.host.toLowerCase(Locale.ROOT).replaceAll(":\\d+$", "");
            if (h.equals(host) || (h.startsWith("*.") && host.endsWith(h.substring(1)))) return true;
        }
        return false;
    }

    private Check runtimeCheck(OtaApp app, InstallerPackage p) {
        String rv = p.getRuntimeVersion();
        if (rv == null || rv.isBlank()) return new Check("运行版本", "warn", "没读到运行版本，没法判断能收到哪些更新");
        List<Release> any = releases.findByAppIdAndRuntimeVersionAndCreatedAtBeforeOrderByCreatedAtDesc(app.getId(), rv,
                Instant.now().plusSeconds(60), PageRequest.of(0, 1));
        if (any.isEmpty()) return new Check("运行版本", "warn", "运行版本 " + rv + " 还没有发布过更新，装了这个包的设备暂时收不到更新");
        return new Check("运行版本", "ok", "运行版本 " + rv + "，已经发布过这个运行版本的更新");
    }

    private Check channelCheck(OtaApp app, InstallerPackage p) {
        String ch = p.getChannel() == null || p.getChannel().isBlank() ? AppService.DEFAULT_CHANNEL : p.getChannel().trim();
        String how = p.getChannel() == null ? "（没设置渠道请求头，按 " + AppService.DEFAULT_CHANNEL + "）" : "";
        Optional<Channel> c = apps.findChannel(app, ch);
        if (c.isEmpty()) return new Check("渠道", "error", "渠道 " + ch + how + " 在这个 App 里不存在，检查更新会失败");
        if (p.getRuntimeVersion() != null) {
            Optional<Deployment> d = deployments.findByChannelIdAndRuntimeVersion(c.get().getId(), p.getRuntimeVersion());
            if (d.isEmpty() || d.get().getReleaseId() == null) {
                return new Check("渠道", "warn", "渠道 " + ch + how + " 还没有发布运行版本 " + p.getRuntimeVersion() + " 的更新");
            }
            return new Check("渠道", "ok", "渠道 " + ch + how + "，线上版本 " + d.get().getReleaseId().substring(0, Math.min(8, d.get().getReleaseId().length())));
        }
        return new Check("渠道", "ok", "渠道 " + ch + how);
    }

    private Check signingCheck(OtaApp app, InstallerPackage p) {
        String bin = p.getCertificatePem(), mine = app.getCertificatePem();
        if (bin == null || bin.isBlank()) {
            return new Check("签名", "warn", "安装包没开更新签名校验：能收到更新，但不验证更新是不是你发的。建议在 app.json 里配置 codeSigningCertificate");
        }
        if (mine == null || mine.isBlank()) return new Check("签名", "error", "安装包要求更新带签名，但这个 App 还没配置签名证书，更新会被拒绝");
        try {
            String a = PemSupport.fingerprint(PemSupport.parseCertificate(bin).getPublicKey().getEncoded());
            String b = PemSupport.fingerprint(PemSupport.parseCertificate(mine).getPublicKey().getEncoded());
            if (!a.equals(b)) return new Check("签名", "error", "安装包内置的证书和这个 App 的签名证书不一样，App 会拒绝更新。去「签名配置」核对");
            // 安装包没写 codeSigningMetadata.keyid 时，expo-updates 按 root 处理
            String kid = p.getKeyId() == null || p.getKeyId().isBlank() ? "root" : p.getKeyId();
            if (app.getKeyId() != null && !kid.equals(app.getKeyId())) {
                return new Check("签名", "error", "证书一致，但 keyid 不一样（安装包 " + kid + "，App " + app.getKeyId() + "），App 会拒绝更新");
            }
            String alg = extra(p).path("signingAlg").asText("");
            if (!alg.isBlank() && !"rsa-v1_5-sha256".equalsIgnoreCase(alg)) {
                return new Check("签名", "error", "安装包的签名算法是 " + alg + "，服务端用 rsa-v1_5-sha256 签名，App 会拒绝更新");
            }
            return new Check("签名", "ok", "内置证书和 App 的签名证书一致，keyid「" + kid + "」");
        } catch (RuntimeException e) {
            return new Check("签名", "warn", "安装包里的签名证书读不懂：" + e.getMessage());
        }
    }

    /**
     * 还在内置版本的设备拿到的是渠道的口令更新包（单独设了的话），否则是线上版本。expo-updates 只装「比正在运行的版本新」的更新，
     * 它的创建时间不晚于安装包内置版本的打包时间时，客户端会认为没有新更新。
     */
    private Check freshnessCheck(OtaApp app, InstallerPackage p) {
        String rv = p.getRuntimeVersion();
        JsonNode extra = extra(p);
        if (rv == null || rv.isBlank()) return new Check("更新新旧", "skip", "没读到运行版本，跳过");
        if (!extra.path("hasEmbeddedManifest").asBoolean(p.getEmbeddedUpdateId() != null)) {
            return new Check("更新新旧", "skip", "安装包没有内置更新，不需要比较");
        }
        Instant built = embeddedTime(extra);
        if (built == null) return new Check("更新新旧", "skip", "内置清单（app.manifest）里没有打包时间，没法比较");
        Optional<Channel> c = apps.findChannel(app, channelOf(p));
        if (c.isEmpty()) return new Check("更新新旧", "skip", "渠道不存在，跳过");
        Optional<Deployment> d = deployments.findByChannelIdAndRuntimeVersion(c.get().getId(), rv);
        Release act = d.map(Deployment::getActivationReleaseId).flatMap(releases::findById).orElse(null);
        if (act != null && act.getPlatforms() != null && List.of(act.getPlatforms().split(",")).contains(p.getPlatform())) {
            if (!act.getCreatedAt().isAfter(built)) {
                return new Check("更新新旧", "error", "口令更新包 " + act.shortId() + " 比内置版本早 " + gap(act.getCreatedAt(), built)
                        + "，新装的设备输入口令激活时会认为已是最新、拿不到更新。打完安装包后需重新上传口令更新包");
            }
            return new Check("更新新旧", "ok", "口令更新包 " + act.shortId() + " 比内置版本晚 " + gap(built, act.getCreatedAt()) + "，新装的设备输入口令激活时会下载");
        }
        if (d.isPresent() && d.get().isRollbackToEmbedded()) return new Check("更新新旧", "ok", "渠道设成了回退内置版本，设备用安装包自带的版本");
        if (d.isEmpty() || d.get().getReleaseId() == null) return new Check("更新新旧", "skip", "渠道上还没有线上版本");
        Optional<Release> live = releases.findById(d.get().getReleaseId());
        if (live.isEmpty()) return new Check("更新新旧", "skip", "渠道上还没有线上版本");
        String id = live.get().shortId();
        if (!live.get().getCreatedAt().isAfter(built)) {
            return new Check("更新新旧", "error", "线上版本 " + id + " 比内置版本早 " + gap(live.get().getCreatedAt(), built)
                    + "，设备会认为已是最新。打完安装包后需重新发布一次");
        }
        return new Check("更新新旧", "ok", "线上版本 " + id + " 比内置版本晚 " + gap(built, live.get().getCreatedAt()) + "，设备会下载");
    }

    private static Instant embeddedTime(JsonNode extra) {
        long ms = extra.path("embeddedCommitMillis").asLong(0);
        if (ms > 0) return Instant.ofEpochMilli(ms);
        String t = extra.path("embeddedCreatedAt").asText("").trim();
        if (t.isEmpty()) return null;
        try {
            return Instant.parse(t);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String gap(Instant from, Instant to) {
        long m = Math.max(0, java.time.Duration.between(from, to).toMinutes());
        if (m < 1) return "不到 1 分钟";
        if (m < 60) return m + " 分钟";
        if (m < 24 * 60) return (m / 60) + " 小时" + (m % 60 == 0 ? "" : " " + (m % 60) + " 分钟");
        return (m / (24 * 60)) + " 天" + ((m / 60) % 24 == 0 ? "" : " " + ((m / 60) % 24) + " 小时");
    }

    private static String channelOf(InstallerPackage p) {
        return p.getChannel() == null || p.getChannel().isBlank() ? AppService.DEFAULT_CHANNEL : p.getChannel().trim();
    }

    /** iOS 是 ALWAYS / WIFI_ONLY / NEVER / ERROR_RECOVERY_ONLY，app.json 里是 ON_LOAD / WIFI_ONLY / NEVER / ON_ERROR_RECOVERY */
    private Check checkOnLaunchCheck(InstallerPackage p) {
        String how = extra(p).path("checkOnLaunch").asText("").trim();
        return switch (how.toUpperCase(Locale.ROOT)) {
            case "" -> new Check("检查时机", "ok", "未设置，按默认每次启动检查（ALWAYS）");
            case "ALWAYS", "ON_LOAD" -> new Check("检查时机", "ok", "每次启动检查（" + how + "）");
            case "WIFI_ONLY" -> new Check("检查时机", "ok", "仅 Wi-Fi 下启动时检查（WIFI_ONLY）");
            case "ERROR_RECOVERY_ONLY", "ON_ERROR_RECOVERY" ->
                    new Check("检查时机", "ok", "仅启动出错时检查（" + how + "），平时由 App 调用 fetchUpdateAsync 更新");
            case "NEVER" -> new Check("检查时机", "warn", "启动时不检查（NEVER），只能由 App 调用 fetchUpdateAsync 更新");
            default -> new Check("检查时机", "ok", "启动时检查（" + how + "）");
        };
    }

    /** iOS ATS 和 Android 都只拦明文 HTTP；HTTPS 换端口没关系，证书受信任就行 */
    private Check transportCheck(InstallerPackage p) {
        String url = p.getUpdateUrl();
        if (url == null || url.isBlank()) return new Check("传输安全", "skip", "没有更新地址");
        String scheme;
        try {
            scheme = URI.create(url.trim()).getScheme();
        } catch (IllegalArgumentException e) {
            return new Check("传输安全", "skip", "更新地址格式不对");
        }
        scheme = scheme == null ? "" : scheme.toLowerCase(Locale.ROOT);
        if ("https".equals(scheme)) return new Check("传输安全", "ok", "更新地址是 HTTPS");
        if (!"http".equals(scheme)) return new Check("传输安全", "error", "更新地址不是 HTTP / HTTPS");
        if (extra(p).path("cleartextAllowed").asBoolean(false)) {
            return new Check("传输安全", "warn", "更新地址是明文 HTTP，安装包允许明文流量，能连上，但清单可能被中途篡改，建议换 HTTPS");
        }
        return new Check("传输安全", "error", "更新地址是明文 HTTP，安装包没有允许明文流量，系统会拦掉请求，App 拉不到更新");
    }

    private Check embeddedCheck(InstallerPackage p) {
        JsonNode extra = extra(p);
        boolean has = extra.path("hasEmbeddedManifest").asBoolean(p.getEmbeddedUpdateId() != null);
        if (!has) {
            return new Check("内置更新", "warn", "安装包里没有内置更新（app.manifest），首次启动要先下载更新，也没法回退内置版本");
        }
        String id = p.getEmbeddedUpdateId();
        return new Check("内置更新", "ok", "有内置更新" + (id == null ? "" : " " + id.substring(0, Math.min(8, id.length()))));
    }

    private static JsonNode extra(InstallerPackage p) {
        if (p.getExtraJson() == null || p.getExtraJson().isBlank()) return JSON.missingNode();
        try {
            return JSON.readTree(p.getExtraJson());
        } catch (IOException e) {
            return JSON.missingNode();
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
