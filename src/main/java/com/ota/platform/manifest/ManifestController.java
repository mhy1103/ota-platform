package com.ota.platform.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.common.Hashes;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.ota.TestDeviceService;
import com.ota.platform.pkg.PackageFiles;
import com.ota.platform.release.Asset;
import com.ota.platform.release.AssetRepository;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseFile;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.signing.CodeSigning;
import com.ota.platform.stats.StatsService;
import com.ota.platform.storage.BlobStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Expo Updates 清单接口，expo-updates 按 app.json 里的 updates.url 请求。 */
@RestController
public class ManifestController {

    private static final Logger log = LoggerFactory.getLogger(ManifestController.class);
    public static final DateTimeFormatter EXPO_DATE = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    private static final Set<String> PLATFORMS = Set.of("ios", "android");
    private static final Map<String, String> MIME = Map.ofEntries(
            Map.entry("png", "image/png"), Map.entry("jpg", "image/jpeg"), Map.entry("jpeg", "image/jpeg"),
            Map.entry("gif", "image/gif"), Map.entry("webp", "image/webp"), Map.entry("svg", "image/svg+xml"),
            Map.entry("ttf", "font/ttf"), Map.entry("otf", "font/otf"), Map.entry("woff", "font/woff"),
            Map.entry("woff2", "font/woff2"), Map.entry("json", "application/json"), Map.entry("mp3", "audio/mpeg"),
            Map.entry("wav", "audio/wav"), Map.entry("mp4", "video/mp4"), Map.entry("js", "application/javascript"),
            Map.entry("hbc", "application/javascript"));

    private final AppService apps;
    private final ReleaseService releases;
    private final AssetRepository assets;
    private final BlobStore blobs;
    private final StatsService stats;
    private final OtaProperties props;
    private final ObjectMapper mapper;
    private final TestDeviceService testDevices;

    public ManifestController(AppService apps, ReleaseService releases, AssetRepository assets, BlobStore blobs,
                              StatsService stats, OtaProperties props, ObjectMapper mapper, TestDeviceService testDevices) {
        this.apps = apps;
        this.releases = releases;
        this.assets = assets;
        this.blobs = blobs;
        this.stats = stats;
        this.props = props;
        this.mapper = mapper;
        this.testDevices = testDevices;
    }

    @GetMapping("/m/{slug}")
    public void manifest(@PathVariable String slug, HttpServletRequest req, HttpServletResponse res) throws IOException {
        serve(slug, req, res);
    }

    private void serve(String slug, HttpServletRequest req, HttpServletResponse res) throws IOException {
        int protocol = parseProtocol(req.getHeader("expo-protocol-version"));
        Optional<OtaApp> found = apps.find(slug);
        if (found.isEmpty()) {
            res.sendError(404, "unknown app");
            return;
        }
        OtaApp app = found.get();
        String platform = firstNonBlank(req.getHeader("expo-platform"), req.getParameter("platform"));
        String runtime = firstNonBlank(req.getHeader("expo-runtime-version"), req.getParameter("runtime-version"));
        if (platform == null || !PLATFORMS.contains(platform) || runtime == null) {
            res.sendError(400, "expo-platform (ios/android) and expo-runtime-version are required");
            return;
        }
        String clientId = req.getHeader("eas-client-id");
        String requested = Objects.requireNonNullElse(firstNonBlank(req.getHeader("expo-channel-name")), AppService.DEFAULT_CHANNEL);
        String channel = testDevices.channelFor(app, clientId).orElse(requested);
        String currentUpdateId = req.getHeader("expo-current-update-id");
        String embeddedUpdateId = req.getHeader("expo-embedded-update-id");
        boolean onEmbedded = currentUpdateId != null && embeddedUpdateId != null
                && currentUpdateId.trim().equalsIgnoreCase(embeddedUpdateId.trim());
        boolean fresh = onEmbedded || currentUpdateId == null || currentUpdateId.isBlank();
        String served = null;

        try {
            Release release = null;
            List<ReleaseFile> files = List.of();
            Optional<ReleaseFile> launch = Optional.empty();
            // 客户端请求口令激活包时带 X-Activation: true 头；没带此头的一律走常规更新。
            // 兼容旧逻辑：fresh 设备且没有 X-Activation 头也走常规更新（先拉到 A+ 再由 A+ 触发口令激活流程）。
            boolean wantsActivation = "true".equalsIgnoreCase(req.getHeader("X-Activation"));
            if (wantsActivation) {
                Release act = releases.activationRelease(app, channel, runtime).orElse(null);
                if (act != null) {
                    List<ReleaseFile> actFiles = releases.files(act.getId(), platform);
                    Optional<ReleaseFile> actLaunch = actFiles.stream().filter(ReleaseFile::isLaunch).findFirst();
                    if (actLaunch.isPresent()) {
                        release = act;
                        files = actFiles;
                        launch = actLaunch;
                    }
                }
            }
            if (release == null) {
                ReleaseService.Resolution picked = releases.resolve(app, channel, runtime, clientId, fresh, testDevices.rolloutFirst(app, clientId));
                if (picked.rollbackToEmbeddedAt() != null) {
                    rollBackToEmbedded(app, protocol, picked.rollbackToEmbeddedAt(), res);
                    return;
                }
                release = picked.release();
                if (release == null) {
                    noUpdate(app, protocol, res);
                    return;
                }
                files = releases.files(release.getId(), platform);
                launch = files.stream().filter(ReleaseFile::isLaunch).findFirst();
                if (launch.isEmpty() && fresh) {
                    // 没进灰度拿到的上一版本没有这个平台的文件：还在内置版本的设备改拿线上版本，口令激活要能下载到更新
                    Release live = releases.liveRelease(app, channel, runtime).orElse(null);
                    if (live != null && !live.getId().equals(release.getId())) {
                        List<ReleaseFile> liveFiles = releases.files(live.getId(), platform);
                        Optional<ReleaseFile> liveLaunch = liveFiles.stream().filter(ReleaseFile::isLaunch).findFirst();
                        if (liveLaunch.isPresent()) {
                            release = live;
                            files = liveFiles;
                            launch = liveLaunch;
                        }
                    }
                }
            }
            if (launch.isEmpty() || release.getId().equalsIgnoreCase(Objects.toString(currentUpdateId, "").trim())) {
                noUpdate(app, protocol, res);
                return;
            }
            String manifestJson = mapper.writeValueAsString(buildManifest(app, release, files, launch.get()));
            String signature = CodeSigning.sign(apps.privateKey(app), manifestJson.getBytes(StandardCharsets.UTF_8));
            MultipartResponse.send(res, protocol == 0 ? 0 : 1, List.of(
                    new MultipartResponse.Part("manifest", manifestJson, signature, app.getKeyId()),
                    new MultipartResponse.Part("extensions", "{\"assetRequestHeaders\":{}}", null, null)));
            served = release.getId();
        } catch (Exception e) {
            log.error("manifest failed for app {} channel {} runtime {}", slug, channel, runtime, e);
            if (!res.isCommitted()) {
                res.reset();
                noUpdate(app, protocol, res);
            }
        } finally {
            Map<String, String> extra = ExtraParams.parse(req.getHeader("expo-extra-params"));
            String fatal = firstNonBlank(req.getHeader("expo-fatal-error"), ExtraParams.launchError(extra));
            stats.record(new StatsService.Check(app.getId(), clientId, platform, runtime, channel,
                    onEmbedded ? StatsService.EMBEDDED : currentUpdateId, served,
                    StatsService.parseFailedHeader(req.getHeader("expo-recent-failed-update-ids")),
                    fatal, ExtraParams.launchErrorUpdate(extra)));
        }
    }

    private Map<String, Object> buildManifest(OtaApp app, Release release, List<ReleaseFile> files, ReleaseFile launch) throws IOException {
        Map<String, Asset> byHash = assets.findAllById(files.stream().map(ReleaseFile::getAssetSha256).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Asset::getSha256, Function.identity()));
        boolean patchable = props.getPatch().isEnabled() && app.isPatchEnabled() && !release.isExternal();

        List<Map<String, Object>> assetList = new ArrayList<>();
        for (ReleaseFile f : files) {
            if (!f.isLaunch()) assetList.add(assetEntry(release, f, byHash, false));
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("id", release.getId());
        manifest.put("createdAt", EXPO_DATE.format(release.getCreatedAt()));
        manifest.put("runtimeVersion", release.getRuntimeVersion());
        manifest.put("launchAsset", assetEntry(release, launch, byHash, patchable));
        manifest.put("assets", assetList);
        manifest.put("metadata", Map.of());

        Map<String, Object> ota = new LinkedHashMap<>();
        ota.put("updateId", release.shortId());
        ota.put("note", Objects.toString(release.getMessage(), ""));
        ota.put("forceUpdate", release.isForceUpdate());
        Map<String, Object> extra = new LinkedHashMap<>();
        JsonNode expoConfig = release.getExpoConfigJson() == null ? null : mapper.readTree(release.getExpoConfigJson());
        extra.put("expoClient", expoConfig == null ? Map.of() : expoConfig);
        extra.put("ota", ota);
        manifest.put("extra", extra);
        return manifest;
    }

    private Map<String, Object> assetEntry(Release release, ReleaseFile f, Map<String, Asset> byHash, boolean viaLaunchEndpoint) {
        Asset asset = byHash.get(f.getAssetSha256());
        if (asset == null) throw new IllegalStateException("asset record missing: " + f.getAssetSha256());
        String ext = f.getExt() == null ? "" : f.getExt().toLowerCase(Locale.ROOT);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("hash", Hashes.base64UrlOfHex(asset.getSha256()));
        m.put("key", asset.getMd5());
        m.put("fileExtension", f.isLaunch() ? ".bundle" : "." + ext);
        m.put("contentType", f.isLaunch() ? "application/javascript" : MIME.getOrDefault(ext, "application/octet-stream"));
        String url;
        if (release.isExternal()) url = release.getExternalBaseUrl() + PackageFiles.encodePath(f.getPath());
        else if (viaLaunchEndpoint) url = props.baseUrl() + "/l/" + asset.getSha256();
        else url = blobs.publicUrl(asset.getSha256());
        m.put("url", url);
        return m;
    }

    private void noUpdate(OtaApp app, int protocol, HttpServletResponse res) throws IOException {
        if (protocol == 0) {
            res.setStatus(204);
            res.setHeader("expo-protocol-version", "0");
            res.setHeader("cache-control", "private, max-age=0");
            return;
        }
        directive(app, "{\"type\":\"noUpdateAvailable\"}", res);
    }

    /** 协议 0 不支持指令，只能返回「无更新」 */
    private void rollBackToEmbedded(OtaApp app, int protocol, Instant commitTime, HttpServletResponse res) throws IOException {
        if (protocol == 0) {
            noUpdate(app, protocol, res);
            return;
        }
        directive(app, "{\"type\":\"rollBackToEmbedded\",\"parameters\":{\"commitTime\":\"" + EXPO_DATE.format(commitTime) + "\"}}", res);
    }

    private void directive(OtaApp app, String json, HttpServletResponse res) throws IOException {
        String signature = CodeSigning.sign(apps.privateKey(app), json.getBytes(StandardCharsets.UTF_8));
        MultipartResponse.send(res, 1, List.of(new MultipartResponse.Part("directive", json, signature, app.getKeyId())));
    }

    private static int parseProtocol(String value) {
        try {
            return Integer.parseInt(Objects.requireNonNullElse(value, "0").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }
}
