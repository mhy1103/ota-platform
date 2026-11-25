package com.ota.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ota.platform.app.AppDeletionService;
import com.ota.platform.app.AppService;
import com.ota.platform.app.Channel;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AppContext;
import com.ota.platform.audit.AuditService;
import com.ota.platform.auth.ApiToken;
import com.ota.platform.auth.TokenPrincipal;
import com.ota.platform.auth.TokenService;
import com.ota.platform.common.ApiException;
import com.ota.platform.ota.*;
import com.ota.platform.pkg.VerifyService;
import com.ota.platform.release.Asset;
import com.ota.platform.release.Deployment;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseFile;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.stats.StatsService;
import com.ota.platform.storage.AssetService;
import com.ota.platform.storage.StorageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Duration;
import java.util.*;

/** 管理接口：App、渠道、令牌、文件上传、版本登记、发布和回滚。用 Authorization: Bearer 令牌访问。 */
@RestController
@RequestMapping("/api/v1")
public class ManagementController {

    private final AppService apps;
    private final ReleaseService releases;
    private final AssetService assetService;
    private final TokenService tokens;
    private final VerifyService verifier;
    private final StatsService stats;
    private final AuditService audit;
    private final AppDeletionService deletion;
    private final StorageService storage;
    private final OtaEvents events;
    private final ReleaseStatsService releaseStats;
    private final TestDeviceService testDevices;
    private final DiagnoseService diagnoses;
    private final com.ota.platform.installer.InstallerService installers;
    private final com.ota.platform.installer.ChunkedUploads chunks;
    private final com.ota.platform.web.admin.ReleaseStructure structure;

    public ManagementController(AppService apps, ReleaseService releases, AssetService assetService, TokenService tokens,
                                VerifyService verifier, StatsService stats, AuditService audit, AppDeletionService deletion,
                                StorageService storage, OtaEvents events, ReleaseStatsService releaseStats,
                                TestDeviceService testDevices, DiagnoseService diagnoses,
                                com.ota.platform.installer.InstallerService installers,
                                com.ota.platform.installer.ChunkedUploads chunks,
                                com.ota.platform.web.admin.ReleaseStructure structure) {
        this.installers = installers;
        this.chunks = chunks;
        this.structure = structure;
        this.apps = apps;
        this.releases = releases;
        this.assetService = assetService;
        this.tokens = tokens;
        this.verifier = verifier;
        this.stats = stats;
        this.audit = audit;
        this.deletion = deletion;
        this.storage = storage;
        this.events = events;
        this.releaseStats = releaseStats;
        this.testDevices = testDevices;
        this.diagnoses = diagnoses;
    }

    // ==================== App ====================

    public record CreateAppRequest(String slug, String name, String keyId, String privateKeyPem, String certificatePem, String downloadUrl) {}

    @PostMapping("/apps")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createApp(@AuthenticationPrincipal TokenPrincipal p, @RequestBody CreateAppRequest req) {
        p.requireAdmin();
        String downloadUrl = AppService.downloadUrl(req.downloadUrl());
        OtaApp app = apps.create(req.slug(), req.name(), req.keyId(), req.privateKeyPem(), req.certificatePem());
        if (downloadUrl != null) apps.setDownloadUrl(app, downloadUrl);
        audit.log("APP_CREATE", "新建 App " + app.getName() + "（" + app.getSlug() + "）· 令牌 " + p.name());
        Map<String, Object> view = appView(app);
        view.put("certificatePem", app.getCertificatePem());
        return view;
    }

    @GetMapping("/apps")
    public List<Map<String, Object>> listApps(@AuthenticationPrincipal TokenPrincipal p) {
        if (p.isAdmin()) return apps.all().stream().map(this::appView).toList();
        return apps.findById(p.appId()).map(a -> List.of(appView(a))).orElse(List.of());
    }

    @GetMapping("/apps/{slug}")
    public Map<String, Object> getApp(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Map<String, Object> view = appView(app);
        List<Map<String, Object>> channelViews = new ArrayList<>();
        for (Channel c : apps.channels(app)) {
            Map<String, Object> cv = new LinkedHashMap<>();
            cv.put("name", c.getName());
            cv.put("deployments", releases.deployments(c).stream().map(this::deploymentView).toList());
            channelViews.add(cv);
        }
        view.put("channels", channelViews);
        return view;
    }

    public record UpdateAppRequest(String name, String downloadUrl) {}

    /** 改 App 名称、下载链接；不传的字段不改，downloadUrl 传空字符串表示清空 */
    @PatchMapping("/apps/{slug}")
    public Map<String, Object> updateApp(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @RequestBody UpdateAppRequest req) {
        p.requireAdmin();
        OtaApp app = app(slug);
        List<String> changed = new ArrayList<>();
        if (req.name() != null) {
            apps.rename(app, req.name());
            changed.add("名称改为 " + app.getName());
        }
        if (req.downloadUrl() != null) {
            apps.setDownloadUrl(app, req.downloadUrl());
            changed.add(app.getDownloadUrl() == null ? "清空下载链接" : "下载链接改为 " + app.getDownloadUrl());
        }
        if (!changed.isEmpty()) audit.log("APP_UPDATE", app.getSlug() + " " + String.join("，", changed) + " · 令牌 " + p.name());
        return appView(app);
    }

    /** App 里 updates.codeSigningCertificate 要用的证书 */
    @GetMapping(value = "/apps/{slug}/certificate", produces = "application/x-pem-file")
    public String certificate(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        return app.getCertificatePem();
    }

    /** 删除 App 和它的全部数据，confirm 参数要填 App 标识，防止误删 */
    @DeleteMapping("/apps/{slug}")
    public Map<String, Object> deleteApp(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                         @RequestParam(required = false) String confirm) {
        p.requireAdmin();
        OtaApp app = app(slug);
        if (!app.getSlug().equals(confirm)) {
            throw ApiException.badRequest("CONFIRM_REQUIRED", "删除 App 要带参数 confirm=" + app.getSlug());
        }
        AppDeletionService.Deleted d = deletion.delete(app);
        audit.log("APP_DELETE", "删除 App " + app.getName() + "（" + app.getSlug() + "）：版本 " + d.releases() + " 个、激活记录 "
                + d.records() + " 条 · 令牌 " + p.name());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("deleted", app.getSlug());
        view.put("releases", d.releases());
        view.put("records", d.records());
        view.put("tokens", d.tokens());
        return view;
    }

    public record RedemptionRequest(Boolean open) {}

    /** 口令兑换开关：open=true 是 B 面（可以兑换），false 是 A 面（审核模式） */
    @PostMapping("/apps/{slug}/redemption")
    public Map<String, Object> redemption(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                          @RequestBody RedemptionRequest req) {
        p.requireAdmin();
        OtaApp app = app(slug);
        boolean open = Boolean.TRUE.equals(req.open());
        apps.setRedeemOpen(app, open);
        audit.log("SWITCH", app.getSlug() + " 切换到 " + (open ? "B面（上线）" : "A面（审核）") + " · 令牌 " + p.name());
        events.modeSwitched(app, open);
        return Map.of("open", open);
    }

    /** 按设备去重开关：开了以后，同一台手机在别的 App 已经算过有效激活，这个 App 就不再算 */
    @PostMapping("/apps/{slug}/dedupe")
    public Map<String, Object> dedupe(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                      @RequestBody RedemptionRequest req) {
        p.requireAdmin();
        OtaApp app = app(slug);
        boolean on = Boolean.TRUE.equals(req.open());
        apps.setDedupeDevices(app, on);
        audit.log("APP_UPDATE", app.getSlug() + (on ? " 开启" : " 关闭") + "按设备去重 · 令牌 " + p.name());
        return Map.of("dedupe", on);
    }

    // ==================== 渠道 ====================

    public record CreateChannelRequest(String name) {}

    @PostMapping("/apps/{slug}/channels")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createChannel(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                             @RequestBody CreateChannelRequest req) {
        p.requireAdmin();
        Channel c = apps.createChannel(app(slug), req.name());
        return Map.of("name", c.getName());
    }

    /** 删除渠道和它的发布记录，版本保留。production 不能删 */
    @DeleteMapping("/apps/{slug}/channels/{channel}")
    public Map<String, Object> deleteChannel(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                             @PathVariable String channel) {
        p.requireAdmin();
        OtaApp app = app(slug);
        apps.deleteChannel(app, channel);
        testDevices.dropChannel(app, channel);
        audit.log("CHANNEL_DELETE", app.getSlug() + " 删除渠道 " + channel + " · 令牌 " + p.name());
        return Map.of("deleted", channel);
    }

    // ==================== 令牌 ====================

    public record CreateTokenRequest(String name) {}

    /** 给 App 创建发布令牌（CI 里用），明文只返回这一次 */
    @PostMapping("/apps/{slug}/tokens")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createToken(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                           @RequestBody(required = false) CreateTokenRequest req) {
        p.requireAdmin();
        OtaApp app = app(slug);
        String name = req == null || req.name() == null || req.name().isBlank() ? "发布令牌" : req.name().trim();
        if (name.length() > 100) name = name.substring(0, 100);
        TokenService.Issued issued = tokens.issue(name, ApiToken.SCOPE_PUBLISH, app.getId());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("name", issued.token().getName());
        view.put("scope", issued.token().getScope());
        view.put("app", app.getSlug());
        view.put("token", issued.plain());
        return view;
    }

    // ==================== 文件 ====================

    public record MissingRequest(List<String> sha256) {}

    /** 传入文件的 SHA-256 列表，返回服务端还没有的，客户端只上传这些 */
    @PostMapping("/apps/{slug}/assets/missing")
    public Map<String, Object> missing(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                       @RequestBody MissingRequest req) {
        p.requireApp(app(slug).getId());
        List<String> shas = req.sha256() == null ? List.of() : req.sha256();
        return Map.of("missing", assetService.missing(shas));
    }

    /** 上传一个文件，请求体就是文件内容；服务端校验内容的 SHA-256 和地址里的一致 */
    @PutMapping(value = "/apps/{slug}/assets/{sha256}", consumes = MediaType.ALL_VALUE)
    public Map<String, Object> upload(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                      @PathVariable String sha256, HttpServletRequest request) throws IOException {
        p.requireApp(app(slug).getId());
        Asset asset = assetService.upload(sha256, request.getInputStream());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("sha256", asset.getSha256());
        view.put("md5", asset.getMd5());
        view.put("size", asset.getSize());
        return view;
    }

    // ==================== 版本 ====================

    public record CreateReleaseRequest(String runtimeVersion, String message, Boolean forceUpdate,
                                       JsonNode metadata, JsonNode expoConfig, Map<String, String> files) {}

    @PostMapping("/apps/{slug}/releases")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRelease(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                             @RequestBody CreateReleaseRequest req) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Release r = releases.create(app, req.runtimeVersion(), req.message(), Boolean.TRUE.equals(req.forceUpdate()),
                req.metadata(), req.expoConfig(), req.files(), p.name(), Release.SOURCE_CLI);
        audit.log("UPLOAD", app.getSlug() + " 登记版本 " + r.shortId() + "（Runtime " + r.getRuntimeVersion() + "，" + r.getPlatforms()
                + "）· 令牌 " + p.name());
        return releaseView(r);
    }

    @GetMapping("/apps/{slug}/releases")
    public List<Map<String, Object>> listReleases(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                                  @RequestParam(defaultValue = "20") int limit) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        List<Release> list = releases.recent(app, limit);
        Map<String, ReleaseStat> st = releaseStats.get(list.stream().map(Release::getId).toList());
        return list.stream().map(r -> {
            Map<String, Object> v = releaseView(r);
            v.put("stats", statView(st.get(r.getId())));
            return v;
        }).toList();
    }

    /** 发布记录：每个渠道的发布、调整灰度、回滚、回退内置版本，最新的在前 */
    @GetMapping("/apps/{slug}/history")
    public List<Map<String, Object>> history(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                             @RequestParam(defaultValue = "30") int limit) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        return releases.history(app, Math.min(Math.max(limit, 1), 200)).stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", e.getAt());
            m.put("channel", e.getChannel());
            m.put("runtimeVersion", e.getRuntime());
            m.put("action", e.getAction().toLowerCase(java.util.Locale.ROOT));
            m.put("releaseId", e.getReleaseId());
            m.put("contentFrom", e.getOriginId());
            m.put("rolloutPercent", e.getRollout());
            m.put("by", e.getActor());
            return m;
        }).toList();
    }

    @GetMapping("/apps/{slug}/releases/{id}")
    public Map<String, Object> getRelease(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @PathVariable String id) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Release r = releases.requireRelease(app, id);
        Map<String, Object> view = releaseView(r);
        List<Map<String, Object>> fileViews = new ArrayList<>();
        for (ReleaseFile f : releases.allFiles(r.getId())) {
            Map<String, Object> fv = new LinkedHashMap<>();
            fv.put("platform", f.getPlatform());
            fv.put("path", f.getPath());
            fv.put("sha256", f.getAssetSha256());
            fv.put("launch", f.isLaunch());
            fileViews.add(fv);
        }
        view.put("files", fileViews);
        view.put("stats", statView(releaseStats.get(r.getId())));
        return view;
    }

    private static Map<String, Object> statView(ReleaseStat s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("offered", s == null ? 0 : s.getOffered());
        m.put("installed", s == null ? 0 : s.getInstalled());
        m.put("failed", s == null ? 0 : s.getFailed());
        m.put("patchDownloads", s == null ? 0 : s.getPatchDownloads());
        m.put("fullDownloads", s == null ? 0 : s.getFullDownloads() + s.getCdnDownloads());
        m.put("bytesSaved", s == null ? 0 : s.getBytesSaved());
        return m;
    }

    public record UpdateReleaseRequest(String message, Boolean forceUpdate) {}

    /** 改版本说明、强制更新，不传的字段不变 */
    @PatchMapping("/apps/{slug}/releases/{id}")
    public Map<String, Object> updateRelease(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @PathVariable String id,
                                             @RequestBody UpdateReleaseRequest req) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Release r = releases.update(app, id, req.message(), req.forceUpdate());
        audit.log("RELEASE_UPDATE", app.getSlug() + " 修改版本 " + r.shortId() + (r.isForceUpdate() ? "（强制更新）" : "")
                + " · 令牌 " + p.name());
        return releaseView(r);
    }

    @DeleteMapping("/apps/{slug}/releases/{id}")
    public Map<String, Object> deleteRelease(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                             @PathVariable String id) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Release r = releases.delete(app, id);
        audit.log("DELETE", app.getSlug() + " 删除版本 " + r.shortId() + " · 令牌 " + p.name());
        return Map.of("deleted", r.getId());
    }

    /** 校验版本：文件、哈希、Runtime、签名；外部链接版本会逐个下载 */
    @PostMapping("/apps/{slug}/releases/{id}/verify")
    public Map<String, Object> verifyRelease(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @PathVariable String id) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        return verifier.verify(app, releases.requireRelease(app, id)).toMap();
    }

    // ==================== 发布、回滚 ====================

    /** as 为 activation 时设成口令更新包（不灰度，rolloutPercent 不用），不传是平常的发布 */
    public record DeployRequest(String releaseId, Integer rolloutPercent, String as) {}

    @PostMapping("/apps/{slug}/channels/{channel}/deploy")
    public Map<String, Object> deploy(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                      @PathVariable String channel, @RequestBody DeployRequest req) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Channel c = apps.requireChannel(app, channel);
        if (req.as() != null && !req.as().isBlank() && !"hot".equalsIgnoreCase(req.as().trim())) {
            if (!"activation".equalsIgnoreCase(req.as().trim())) throw ApiException.badRequest("BAD_AS", "as 只能是 hot（常规更新）或 activation（口令更新包）");
            ReleaseService.Activation a = releases.setActivation(app, c, req.releaseId(), p.name());
            if (a.changed()) {
                events.activationDeployed(app, a.release(), c.getName());
                audit.log("ACTIVATION_PACKAGE", app.getSlug() + " · " + c.getName() + " 设口令更新包 " + a.release().shortId()
                        + "（Runtime " + a.release().getRuntimeVersion() + "）· 令牌 " + p.name());
            }
            Map<String, Object> view = deploymentView(a.deployment());
            view.put("channel", c.getName());
            view.put("as", "activation");
            view.put("changed", a.changed());
            return view;
        }
        int rollout = req.rolloutPercent() == null ? 100 : req.rolloutPercent();
        ReleaseService.Deployed d = releases.deploy(app, c, req.releaseId(), rollout, p.name());
        events.deployed(app, d.release(), c.getName(), rollout, d.republished());
        audit.log("ACTIVATE", app.getSlug() + " · " + c.getName() + " 发布 " + d.release().shortId() + "（Runtime "
                + d.release().getRuntimeVersion() + "，灰度 " + rollout + "%）" + (d.republished() ? " · 重新发布" : "")
                + " · 令牌 " + p.name());
        return deployedView(c, d);
    }

    public record RuntimeRequest(String runtimeVersion) {}

    public record RollbackRequest(String runtimeVersion, String to) {}

    /** 回滚：to 为空回到上一版本，也可以指定更早的任意版本（完整 ID 或 6 位以上前缀）。旧版本复制成新版本发布，已经更新的设备也会换回去 */
    @PostMapping("/apps/{slug}/channels/{channel}/rollback")
    public Map<String, Object> rollback(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                        @PathVariable String channel, @RequestBody RollbackRequest req) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Channel c = apps.requireChannel(app, channel);
        ReleaseService.Deployed d = releases.rollback(app, c, req.runtimeVersion(), req.to(), p.name());
        events.rolledBack(app, d.release(), c.getName());
        audit.log("ROLLBACK", app.getSlug() + " · " + c.getName() + " 回滚到 " + d.release().shortId()
                + "（Runtime " + d.release().getRuntimeVersion() + "）· 令牌 " + p.name());
        return deployedView(c, d);
    }

    /** 取消口令更新包：还在用安装包内置版本的设备（输入口令激活的）改回拿常规更新包 */
    @PostMapping("/apps/{slug}/channels/{channel}/activation/clear")
    public Map<String, Object> clearActivation(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                               @PathVariable String channel, @RequestBody RuntimeRequest req) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Channel c = apps.requireChannel(app, channel);
        Deployment d = releases.clearActivation(app, c, req.runtimeVersion(), p.name());
        events.activationCleared(app, c.getName(), d.getRuntimeVersion());
        audit.log("ACTIVATION_PACKAGE", app.getSlug() + " · " + c.getName() + " 取消口令更新包（Runtime " + d.getRuntimeVersion() + "）· 令牌 " + p.name());
        Map<String, Object> view = deploymentView(d);
        view.put("channel", c.getName());
        return view;
    }

    /** 让这个渠道、这个 Runtime 的设备回到安装包内置的版本，再次发布版本即恢复 */
    @PostMapping("/apps/{slug}/channels/{channel}/rollback-embedded")
    public Map<String, Object> rollbackEmbedded(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                                @PathVariable String channel, @RequestBody RuntimeRequest req) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Channel c = apps.requireChannel(app, channel);
        Deployment d = releases.rollbackToEmbedded(app, c, req.runtimeVersion(), p.name());
        events.rolledBackToEmbedded(app, c.getName(), d.getRuntimeVersion());
        audit.log("ROLLBACK", app.getSlug() + " · " + c.getName() + " 回退到内置版本（Runtime " + d.getRuntimeVersion() + "）· 令牌 " + p.name());
        Map<String, Object> view = deploymentView(d);
        view.put("channel", c.getName());
        return view;
    }

    // ==================== 测试设备、诊断 ====================

    /** rolloutFirst 可以不传：不传就不改原来的设置 */
    public record TestDeviceRequest(String clientId, String name, String channel, Boolean rolloutFirst) {}

    @GetMapping("/apps/{slug}/test-devices")
    public List<Map<String, Object>> testDevices(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        return testDevices.list(app).stream().map(t -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.getId());
            m.put("clientId", t.getClientId());
            m.put("name", t.getName());
            m.put("channel", t.getChannel());
            m.put("rolloutFirst", t.isRolloutFirst());
            m.put("createdAt", t.getCreatedAt());
            return m;
        }).toList();
    }

    /** 添加或修改测试设备：这台设备检查更新时改用指定渠道 */
    @PostMapping("/apps/{slug}/test-devices")
    public Map<String, Object> saveTestDevice(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                              @RequestBody TestDeviceRequest req) {
        p.requireAdmin();
        OtaApp app = app(slug);
        TestDevice t = testDevices.save(app, req.clientId(), req.name(), req.channel(), req.rolloutFirst());
        audit.log("TESTER_SAVE", app.getSlug() + " 测试设备 " + t.getClientId() + " → 渠道 " + t.getChannel()
                + (t.isRolloutFirst() ? "，灰度白名单" : "") + " · 令牌 " + p.name());
        return Map.of("id", t.getId(), "clientId", t.getClientId(), "channel", t.getChannel());
    }

    @DeleteMapping("/apps/{slug}/test-devices/{id}")
    public Map<String, Object> deleteTestDevice(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @PathVariable Long id) {
        p.requireAdmin();
        OtaApp app = app(slug);
        TestDevice t = testDevices.remove(app, id);
        audit.log("TESTER_DELETE", app.getSlug() + " 移除测试设备 " + t.getClientId() + " · 令牌 " + p.name());
        return Map.of("deleted", t.getId());
    }

    /** 模拟检查更新：这台设备会拿到什么版本、为什么 */
    @GetMapping("/apps/{slug}/diagnose")
    public Map<String, Object> diagnose(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                        @RequestParam(defaultValue = "") String device, @RequestParam(defaultValue = "ios") String platform,
                                        @RequestParam(defaultValue = "") String runtime, @RequestParam(defaultValue = "") String channel) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        DiagnoseService.Result r = diagnoses.diagnose(app, device, platform, runtime, channel);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("verdict", r.verdict());
        m.put("level", r.level());
        m.put("releaseId", r.releaseId());
        m.put("steps", r.steps());
        return m;
    }

    // ==================== 统计 ====================

    /** 最近 30 天检查过更新的设备，按正在运行的版本和平台统计 */
    @GetMapping("/apps/{slug}/stats")
    public Map<String, Object> stats(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("activeDevices", stats.activeDevices(app.getId()));
        m.put("byUpdate", stats.devicesByUpdate(app.getId()));
        m.put("byPlatform", stats.devicesByPlatform(app.getId()));
        return m;
    }

    // ==================== 存储 ====================

    @GetMapping("/storage")
    public Map<String, Object> storage(@AuthenticationPrincipal TokenPrincipal p) {
        p.requireAdmin();
        StorageService.Usage u = storage.usage();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("files", u.files());
        m.put("bytes", u.bytes());
        m.put("patches", u.patches());
        m.put("patchBytes", u.patchBytes());
        return m;
    }

    /** 清理没有版本引用的文件和用不上的补丁；minAgeHours 之内登记的文件不动 */
    @PostMapping("/storage/clean")
    public Map<String, Object> cleanStorage(@AuthenticationPrincipal TokenPrincipal p, @RequestParam(defaultValue = "24") int minAgeHours) {
        p.requireAdmin();
        StorageService.Cleaned c = storage.clean(Duration.ofHours(Math.max(0, minAgeHours)));
        audit.log("STORAGE_CLEAN", "清理存储：文件 " + c.files() + " 个（" + c.bytes() + " 字节）、补丁记录 " + c.patches() + " 条 · 令牌 " + p.name());
        return Map.of("files", c.files(), "bytes", c.bytes(), "patches", c.patches());
    }

    // ==================== 安装包 ====================

    @GetMapping("/apps/{slug}/installers")
    public List<Map<String, Object>> listInstallers(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        return installers.list(app).stream().map(i -> installerView(app, i)).toList();
    }

    /** 一次传完（小文件）；大文件经过反向代理时用下面的分片上传 */
    @PostMapping(value = "/apps/{slug}/installers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> uploadInstaller(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                               @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
                                               @RequestParam(defaultValue = "") String note) throws java.io.IOException {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        return installerUploaded(app, installers.upload(app, file, note, "令牌：" + p.name()), p);
    }

    public record StartUploadRequest(String fileName, Long size) {}

    @PostMapping("/apps/{slug}/installers/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> startInstallerUpload(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug,
                                                    @RequestBody StartUploadRequest req) throws java.io.IOException {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        String id = chunks.start("令牌：" + p.name(), app.getId(), req.fileName(), req.size() == null ? 0 : req.size());
        return Map.of("id", id, "chunkSize", 8 * 1024 * 1024);
    }

    @PutMapping(value = "/apps/{slug}/installers/uploads/{id}", consumes = MediaType.ALL_VALUE)
    public Map<String, Object> appendInstallerUpload(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @PathVariable String id,
                                                     @RequestParam long offset, jakarta.servlet.http.HttpServletRequest req) throws java.io.IOException {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        return Map.of("received", chunks.append(id, "令牌：" + p.name(), app.getId(), offset, req.getInputStream()));
    }

    public record CompleteUploadRequest(String note) {}

    @PostMapping("/apps/{slug}/installers/uploads/{id}/complete")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> completeInstallerUpload(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @PathVariable String id,
                                                       @RequestBody(required = false) CompleteUploadRequest req) throws java.io.IOException {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        com.ota.platform.installer.ChunkedUploads.Done done = chunks.finish(id, "令牌：" + p.name(), app.getId());
        try {
            return installerUploaded(app, installers.store(app, done.file(), done.fileName(), req == null ? null : req.note(), "令牌：" + p.name()), p);
        } finally {
            java.nio.file.Files.deleteIfExists(done.file());
        }
    }

    @DeleteMapping("/apps/{slug}/installers/{id}")
    public Map<String, Object> deleteInstaller(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @PathVariable String id) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        com.ota.platform.installer.InstallerPackage i = installers.find(app, id)
                .orElseThrow(() -> ApiException.notFound("NO_INSTALLER", "没有这个安装包"));
        installers.delete(i);
        audit.log("INSTALLER_DELETE", app.getSlug() + " 删除安装包 " + i.getFileName() + " · 令牌 " + p.name());
        return Map.of("deleted", id);
    }

    private Map<String, Object> installerUploaded(OtaApp app, com.ota.platform.installer.InstallerPackage i, TokenPrincipal p) {
        audit.log("INSTALLER_UPLOAD", app.getSlug() + " 上传安装包 " + i.getFileName() + "（" + com.ota.platform.installer.InstallerService.title(i)
                + "，运行版本 " + (i.getRuntimeVersion() == null ? "未知" : i.getRuntimeVersion()) + "）· 令牌 " + p.name());
        return installerView(app, i);
    }

    private Map<String, Object> installerView(OtaApp app, com.ota.platform.installer.InstallerPackage i) {
        List<com.ota.platform.installer.InstallerService.Check> checks = installers.checks(app, i);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("platform", i.getPlatform());
        m.put("fileName", i.getFileName());
        m.put("size", i.getSize());
        m.put("sha256", i.getSha256());
        m.put("bundleId", i.getBundleId());
        m.put("version", i.getVersionName());
        m.put("buildNumber", i.getBuildNumber());
        m.put("runtimeVersion", i.getRuntimeVersion());
        m.put("updateUrl", i.getUpdateUrl());
        m.put("channel", i.getChannel());
        m.put("keyId", i.getKeyId());
        m.put("embeddedUpdateId", i.getEmbeddedUpdateId());
        m.put("sdkVersion", i.getSdkVersion());
        if (i.getExtraJson() != null) m.put("extra", i.getExtraJson());
        m.put("note", i.getNote());
        m.put("uploadedBy", i.getUploadedBy());
        m.put("uploadedAt", i.getUploadedAt());
        m.put("level", com.ota.platform.installer.InstallerService.level(checks));
        m.put("checks", checks.stream().map(c -> Map.of("label", c.label(), "state", c.state(), "text", c.text())).toList());
        return m;
    }

    // ==================== 更新包结构 ====================

    /** 版本的全部文件；带 base（另一个版本的 ID）时标出新增、有变化、没变的文件和从 base 更新过来要下载的大小 */
    @GetMapping("/apps/{slug}/releases/{id}/structure")
    public Map<String, Object> releaseStructure(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @PathVariable String id,
                                                @RequestParam(required = false) String base) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Release r = releases.requireRelease(app, id);
        Release b = base == null || base.isBlank() ? null : releases.requireRelease(app, base);
        return structure.build(r, b);
    }

    // ==================== 视图 ====================

    private Map<String, Object> appView(OtaApp app) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("slug", app.getSlug());
        m.put("name", app.getName());
        m.put("downloadUrl", app.getDownloadUrl());
        m.put("keyId", app.getKeyId());
        m.put("redemptionOpen", app.isRedeemOpen());
        m.put("patchEnabled", app.isPatchEnabled());
        m.put("createdAt", app.getCreatedAt());
        return m;
    }

    private Map<String, Object> releaseView(Release r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("runtimeVersion", r.getRuntimeVersion());
        m.put("platforms", Arrays.asList(r.getPlatforms().split(",")));
        m.put("message", r.getMessage());
        m.put("forceUpdate", r.isForceUpdate());
        m.put("totalBytes", r.getTotalBytes());
        m.put("source", r.getSource());
        m.put("externalBaseUrl", r.getExternalBaseUrl());
        m.put("republishedFrom", r.getRepublishedFrom());
        m.put("createdBy", r.getCreatedBy());
        m.put("createdAt", r.getCreatedAt());
        return m;
    }

    private Map<String, Object> deploymentView(Deployment d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("runtimeVersion", d.getRuntimeVersion());
        m.put("releaseId", d.getReleaseId());
        m.put("rolloutPercent", d.getRolloutPercent());
        m.put("previousReleaseId", d.getPreviousReleaseId());
        m.put("rollbackToEmbedded", d.isRollbackToEmbedded());
        m.put("updatedBy", d.getUpdatedBy());
        m.put("updatedAt", d.getUpdatedAt());
        if (d.getActivationReleaseId() != null) {
            m.put("activationReleaseId", d.getActivationReleaseId());
            m.put("activationBy", d.getActivationBy());
            m.put("activationAt", d.getActivationAt());
        }
        return m;
    }

    private Map<String, Object> deployedView(Channel c, ReleaseService.Deployed d) {
        Map<String, Object> view = deploymentView(d.deployment());
        view.put("channel", c.getName());
        view.put("republished", d.republished());
        return view;
    }

    /** 解析地址里的 App，并记到这次请求上（操作日志带上 App） */
    private OtaApp app(String slug) {
        OtaApp a = apps.require(slug);
        AppContext.set(a);
        return a;
    }
}
