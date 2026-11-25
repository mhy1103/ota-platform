package com.ota.platform.web.admin;

import com.ota.platform.web.AdminUi;
import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AdminUserService;
import com.ota.platform.admin.AppPerm;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.app.AppDeletionService;
import com.ota.platform.app.AppService;
import com.ota.platform.app.Channel;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.audit.OperationLogRepo;
import com.ota.platform.auth.ApiToken;
import com.ota.platform.auth.TokenService;
import com.ota.platform.backup.BackupService;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.codes.ActivationRecordRepo;
import com.ota.platform.common.ApiException;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.ota.*;
import com.ota.platform.release.ReleaseRepository;
import com.ota.platform.signing.PemSupport;
import com.ota.platform.signing.SigningInspector;
import com.ota.platform.stats.StatsService;
import com.ota.platform.webhook.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** 所有 App（列表、新建、从备份恢复）和当前 App 的设置（接入、渠道、令牌、口令接口、Webhook、备份、删除）。 */
@Controller
@RequestMapping("/admin")
public class AdminAppsController {

    private final AppService apps;
    private final AppDeletionService deletion;
    private final TokenService tokens;
    private final WebhookService webhooks;
    private final ReleaseRepository releaseRepo;
    private final ActivationCodeRepo codeRepo;
    private final ActivationRecordRepo recordRepo;
    private final StatsService stats;
    private final ReleaseViews releaseViews;
    private final AdminShell shell;
    private final AuditService audit;
    private final OtaProperties props;
    private final BackupService backups;
    private final AccessService access;
    private final AdminUserService adminUsers;
    private final TestDeviceService testDevices;
    private final NotifyService notify;
    private final ReleaseStatsService releaseStats;
    private final LaunchErrorService launchErrors;
    private final OperationLogRepo logRepo;
    private final SigningInspector signingInspector;
    private final AdminUi ui;

    private final com.ota.platform.webhook.NotifyDeliveries deliveries;
    private final com.ota.platform.app.AppIcons icons;
    private final com.ota.platform.codes.ActiveDays activeDays;

    public AdminAppsController(AppService apps, AppDeletionService deletion, TokenService tokens, WebhookService webhooks,
                               ReleaseRepository releaseRepo, ActivationCodeRepo codeRepo, ActivationRecordRepo recordRepo,
                               StatsService stats, ReleaseViews releaseViews, AdminShell shell, AuditService audit, OtaProperties props,
                               BackupService backups, AccessService access, AdminUserService adminUsers, TestDeviceService testDevices,
                               NotifyService notify, ReleaseStatsService releaseStats, LaunchErrorService launchErrors,
                               OperationLogRepo logRepo, SigningInspector signingInspector, AdminUi ui,
                               com.ota.platform.webhook.NotifyDeliveries deliveries, com.ota.platform.app.AppIcons icons,
                               com.ota.platform.codes.ActiveDays activeDays) {
        this.activeDays = activeDays;
        this.deliveries = deliveries;
        this.icons = icons;
        this.apps = apps;
        this.deletion = deletion;
        this.tokens = tokens;
        this.webhooks = webhooks;
        this.releaseRepo = releaseRepo;
        this.codeRepo = codeRepo;
        this.recordRepo = recordRepo;
        this.stats = stats;
        this.releaseViews = releaseViews;
        this.shell = shell;
        this.audit = audit;
        this.props = props;
        this.backups = backups;
        this.access = access;
        this.adminUsers = adminUsers;
        this.testDevices = testDevices;
        this.notify = notify;
        this.releaseStats = releaseStats;
        this.launchErrors = launchErrors;
        this.logRepo = logRepo;
        this.signingInspector = signingInspector;
        this.ui = ui;
    }

    /** 所有 App 页的一张卡片 */
    public static class AppCard {
        private final OtaApp app;
        private final ReleaseViews.Live live;
        private final long releases;
        private final long today;
        private final long confirmed;
        private final long devices;
        private final String fingerprint;
        private final List<String> sharedWith;

        AppCard(OtaApp app, ReleaseViews.Live live, long releases, long today, long confirmed, long devices,
                String fingerprint, List<String> sharedWith) {
            this.app = app;
            this.live = live;
            this.releases = releases;
            this.today = today;
            this.confirmed = confirmed;
            this.devices = devices;
            this.fingerprint = fingerprint;
            this.sharedWith = sharedWith;
        }

        /** 证书公钥指纹前几段 */
        public String getFingerprint() { return fingerprint; }
        /** 和哪些 App 用的是同一对签名密钥 */
        public List<String> getSharedWith() { return sharedWith; }

        public OtaApp getApp() { return app; }
        /** production 渠道最近更新的线上状态，没发布过是 null */
        public ReleaseViews.Live getLive() { return live; }
        public long getReleases() { return releases; }
        /** 今天的有效激活 */
        public long getToday() { return today; }
        public long getConfirmed() { return confirmed; }
        public long getDevices() { return devices; }
    }

    /** App 设置页渠道表的一行 */
    public static class ChannelRow {
        private final Channel channel;
        private final List<ReleaseViews.Live> live;

        ChannelRow(Channel channel, List<ReleaseViews.Live> live) {
            this.channel = channel;
            this.live = live;
        }

        public Channel getChannel() { return channel; }
        public List<ReleaseViews.Live> getLive() { return live; }
        public boolean isDefaultChannel() { return AppService.DEFAULT_CHANNEL.equals(channel.getName()); }
    }

    // ==================== 页面 ====================

    /** 看板里「需要注意」的一条 */
    public record Notice(String level, OtaApp app, String text, String link, String action) {}

    @GetMapping("/apps")
    public String list(Model model, HttpSession session) {
        shell.populate(model, session);
        List<OtaApp> visible = shell.visible();
        Map<Long, String> prints = new HashMap<>();
        Map<String, List<String>> byPrint = new HashMap<>();
        for (OtaApp a : visible) {
            String fp = certPrint(a);
            prints.put(a.getId(), fp);
            if (fp != null) byPrint.computeIfAbsent(fp, k -> new ArrayList<>()).add(a.getName());
        }
        List<AppCard> cards = new ArrayList<>();
        List<Notice> notices = new ArrayList<>();
        long devices = 0, today = 0, week = 0, errors = 0;
        LocalDateTime start = ui.startOfToday();
        for (OtaApp a : visible) {
            List<ReleaseViews.Live> lives = releaseViews.live(a);
            ReleaseViews.Live live = lives.stream()
                    .filter(l -> AppService.DEFAULT_CHANNEL.equals(l.getChannel()))
                    .filter(l -> l.getRelease() != null || l.isRollbackToEmbedded())
                    .max(Comparator.comparing(ReleaseViews.Live::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder())))
                    .orElse(null);
            long active = stats.activeDevices(a.getId());
            devices += active;
            long confirmedToday = recordRepo.countByAppIdAndConfirmedTrueAndConfirmedAtGreaterThanEqual(a.getId(), start);
            today += confirmedToday;
            week += releaseRepo.countByAppIdAndCreatedAtAfter(a.getId(), java.time.Instant.now().minus(java.time.Duration.ofDays(7)));
            long err = launchErrors.countSince(a.getId(), java.time.Duration.ofDays(1));
            errors += err;
            String fp = prints.get(a.getId());
            List<String> shared = fp == null ? List.of() : byPrint.get(fp).stream().filter(n -> !n.equals(a.getName())).toList();
            cards.add(new AppCard(a, live, releaseRepo.countByAppId(a.getId()), confirmedToday,
                    recordRepo.countByAppIdAndConfirmedTrue(a.getId()), active, fp, shared));
            notices.addAll(notices(a, lives));
        }
        model.addAttribute("cards", cards);
        model.addAttribute("notices", notices);
        model.addAttribute("sumDevices", devices);
        model.addAttribute("sumToday", today);
        model.addAttribute("sumWeek", week);
        model.addAttribute("sumErrors", errors);
        model.addAttribute("owner", access.owner());
        model.addAttribute("reuseApps", visible);
        return "admin/apps";
    }

    /** 这个 App 需要注意的事：签名、启动失败、没下载过备份 */
    private List<Notice> notices(OtaApp a, List<ReleaseViews.Live> lives) {
        List<Notice> out = new ArrayList<>();
        String back = "/admin/use?app=" + a.getSlug() + "&back=";
        SigningInspector.Status sig = signingInspector.inspect(a);
        if (!"ok".equals(sig.getLevel())) {
            out.add(new Notice("error".equals(sig.getLevel()) ? "error" : "warn", a, sig.getHeadline(), back + "/admin/signing", "查看签名"));
        }
        Map<String, ReleaseStat> st = releaseStats.get(lives.stream().filter(l -> l.getRelease() != null).map(l -> l.getRelease().getId()).toList());
        for (ReleaseViews.Alarm x : ReleaseViews.alarms(lives, st)) {
            out.add(new Notice("error", a, x.channels() + " 的版本 " + x.release().shortId() + " 有 " + x.stat().getFailed() + " 台设备启动失败（"
                    + x.stat().getFailRate() + "%），建议回滚", back + "/admin/releases", "去回滚"));
        }
        if (access.can(a, AppPerm.SIGNING) && !logRepo.existsByActionAndDetailStartingWith("APP_BACKUP", a.getSlug() + " ")) {
            out.add(new Notice("info", a, "还没有下载过备份，签名密钥丢了已安装的 App 就收不到更新", back + "/admin/app#backup", "下载备份"));
        }
        return out;
    }

    /** 证书公钥指纹（前 4 段），同一对密钥的 App 指纹相同 */
    private static String certPrint(OtaApp a) {
        try {
            String fp = PemSupport.fingerprint(PemSupport.parseCertificate(a.getCertificatePem()).getPublicKey().getEncoded());
            return fp.length() > 11 ? fp.substring(0, 11) : fp;
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/app")
    public String settings(Model model, HttpSession session) {
        OtaApp app = shell.populate(model, session);
        if (app == null) return "redirect:/admin/apps";
        app = apps.ensureCodeKey(app);
        model.addAttribute("app", app);
        List<ReleaseViews.Live> live = releaseViews.live(app);
        List<ChannelRow> channelRows = new ArrayList<>();
        for (Channel c : apps.channels(app)) {
            channelRows.add(new ChannelRow(c, live.stream().filter(l -> l.getChannel().equals(c.getName())).toList()));
        }
        model.addAttribute("channelRows", channelRows);
        model.addAttribute("tokens", tokens.forApp(app.getId()));
        model.addAttribute("codeSecret", apps.codeSecret(app));
        model.addAttribute("webhookSecret", apps.webhookSecret(app));
        model.addAttribute("baseUrl", props.baseUrl());
        model.addAttribute("manifestUrl", props.baseUrl() + "/m/" + app.getSlug());
        model.addAttribute("releaseCount", releaseRepo.countByAppId(app.getId()));
        model.addAttribute("recordCount", recordRepo.countByAppId(app.getId()));
        model.addAttribute("deviceCount", stats.activeDevices(app.getId()));
        model.addAttribute("testers", testDevices.list(app));
        model.addAttribute("notifyKind", app.getNotifyUrl() == null ? null : NotifyService.kind(app.getNotifyUrl()));
        java.util.Set<String> muted = notify.muted(app.getId());
        model.addAttribute("notifyEvents", NotifyService.EVENTS.stream().map(e -> {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", e.id());
            row.put("name", e.name());
            row.put("desc", e.desc());
            row.put("on", !muted.contains(e.id()));
            return row;
        }).toList());
        model.addAttribute("canSettings", access.can(app, AppPerm.SETTINGS));
        model.addAttribute("fpKeepOptions", List.of(7, 30, 90, 0));
        model.addAttribute("fpKeepDefault", OtaApp.DEFAULT_FINGERPRINT_KEEP_DAYS);
        model.addAttribute("deliveries", access.can(app, AppPerm.SETTINGS) ? deliveries.recent(app.getId()) : List.of());
        model.addAttribute("canSigning", access.can(app, AppPerm.SIGNING));
        model.addAttribute("canRelease", access.can(app, AppPerm.RELEASE));
        model.addAttribute("owner", access.owner());
        return "admin/app";
    }

    // ==================== 新建、改名 ====================

    @PostMapping("/apps/create")
    public String create(@RequestParam String slug, @RequestParam(defaultValue = "") String name,
                         @RequestParam(defaultValue = "") String downloadUrl,
                         @RequestParam(defaultValue = "main") String keyId,
                         @RequestParam(defaultValue = "generate") String keyMode,
                         @RequestParam(defaultValue = "") String reuseFrom,
                         @RequestParam(value = "privateKey", required = false) MultipartFile privateKey,
                         @RequestParam(value = "certificate", required = false) MultipartFile certificate,
                         HttpSession session, RedirectAttributes ra) {
        try {
            String download = AppService.downloadUrl(downloadUrl);
            OtaApp app;
            String how;
            if ("reuse".equals(keyMode)) {
                OtaApp source = apps.require(reuseFrom);
                AppService.SigningMaterial m = apps.signingMaterial(source);
                app = apps.create(slug, name, m.keyId(), m.privateKeyPem(), m.certificatePem());
                how = "复用 " + source.getName() + " 的签名密钥";
            } else {
                app = apps.create(slug, name, keyId, text(privateKey), text(certificate));
                how = privateKey != null && !privateKey.isEmpty() ? "导入现有签名密钥" : "自动生成签名密钥";
            }
            if (download != null) apps.setDownloadUrl(app, download);
            audit.log(app.getId(), "APP_CREATE", "新建 App " + app.getName() + "（" + app.getSlug() + "），" + how
                    + (download != null ? "，下载链接 " + download : ""));
            shell.use(session, app.getSlug());
            ra.addFlashAttribute("msg", "已创建 " + app.getName() + "，" + how + "。下载证书放进 App，按下面的配置接入");
            return "redirect:/admin/app";
        } catch (ApiException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", "未创建：" + e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "未创建：" + e.getClass().getSimpleName());
        }
        return "redirect:/admin/apps";
    }

    @PostMapping("/apps/rename")
    public String rename(@RequestParam String name, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "basic", app -> {
            apps.rename(app, name);
            audit.log("APP_UPDATE", app.getSlug() + " 改名为 " + name.trim());
            return "已改名为 " + name.trim();
        });
    }

    @PostMapping("/apps/icon")
    public String uploadIcon(@RequestParam("file") MultipartFile file, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "basic", app -> {
            try {
                icons.set(app, file.getBytes());
            } catch (java.io.IOException e) {
                throw ApiException.badRequest("ICON_READ", "图片没传上来，再试一次");
            }
            audit.log("APP_UPDATE", app.getSlug() + " 换了图标");
            return "已更新图标";
        });
    }

    @PostMapping("/apps/icon/delete")
    public String deleteIcon(HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "basic", app -> {
            icons.remove(app);
            audit.log("APP_UPDATE", app.getSlug() + " 去掉了图标");
            return "已去掉图标，改回显示名字的第一个字";
        });
    }

    /** App 图标：能看到这个 App 的管理员才能取；地址带版本号，浏览器一直缓存 */
    @GetMapping("/apps/{id}/icon")
    public ResponseEntity<byte[]> icon(@PathVariable Long id) {
        CurrentAdmin me = access.current();
        if (me == null || !me.canSee(id)) return ResponseEntity.notFound().build();
        return icons.get(id).map(AdminAppsController::iconResponse).orElse(ResponseEntity.notFound().build());
    }

    public static ResponseEntity<byte[]> iconResponse(com.ota.platform.app.AppIcon i) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(i.getContentType()))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=31536000, immutable")
                .header("X-Content-Type-Options", "nosniff")
                .body(i.getData());
    }

    @PostMapping("/apps/download-url")
    public String downloadUrl(@RequestParam(defaultValue = "") String url, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "basic", app -> {
            apps.setDownloadUrl(app, url);
            String now = app.getDownloadUrl();
            audit.log("APP_UPDATE", app.getSlug() + (now == null ? " 清空下载链接" : " 下载链接改为 " + now));
            return now == null ? "已清空下载链接" : "已保存下载链接";
        });
    }

    @PostMapping("/apps/patch")
    public String patch(@RequestParam boolean enabled, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "basic", app -> {
            apps.setPatchEnabled(app, enabled);
            audit.log("APP_UPDATE", app.getSlug() + (enabled ? " 开启" : " 关闭") + "增量更新");
            return enabled ? "已开启增量更新" : "已关闭增量更新，设备都下载完整启动包";
        });
    }

    @PostMapping("/apps/dedupe")
    public String dedupe(@RequestParam boolean enabled, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "basic", app -> {
            OtaApp a = apps.setDedupeDevices(app, enabled);
            audit.log("APP_UPDATE", app.getSlug() + (enabled ? " 开启" : " 关闭") + "按设备去重");
            if (!enabled) return "已关闭按设备去重：这个 App 的激活各算各的，和别的 App 不相干";
            int n = activeDays.pruneNow(a);
            return "已开启按设备去重：同一台手机在别的 App 算过有效激活，这个 App 就不再算"
                    + (n > 0 ? "；" + n + " 台超过 " + a.getFingerprintKeepDays() + " 天没打开的设备，指纹现在就清掉" : "");
        });
    }

    @PostMapping("/apps/dedupe-keep")
    public String dedupeKeep(@RequestParam int days, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "basic", app -> {
            OtaApp a = apps.setFingerprintKeepDays(app, days);
            audit.log("APP_UPDATE", app.getSlug() + " 设备指纹保留" + (days <= 0 ? "一直留着" : days + " 天"));
            if (days <= 0) return "设备指纹一直留着，设备再久没打开也按同一台算";
            String rule = "设备 " + days + " 天没打开就清掉指纹，之后再激活重新算";
            if (!a.isDedupeDevices()) return rule + "（开了按设备去重以后生效）";
            int n = activeDays.pruneNow(a);
            return rule + "；" + (n > 0 ? n + " 台已经超过 " + days + " 天没打开的设备，指纹现在就清掉" : "现在没有超过 " + days + " 天没打开的设备");
        });
    }

    // ==================== 渠道 ====================

    @PostMapping("/apps/channel")
    public String channel(@RequestParam String name, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "channels", app -> {
            Channel c = apps.createChannel(app, name);
            audit.log("CHANNEL_CREATE", app.getSlug() + " 新建渠道 " + c.getName());
            return "已新建渠道 " + c.getName() + "。App 用请求头 expo-channel-name: " + c.getName() + " 选择它";
        });
    }

    @PostMapping("/apps/channel/delete")
    public String deleteChannel(@RequestParam String name, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "channels", app -> {
            apps.deleteChannel(app, name);
            int testers = testDevices.dropChannel(app, name);
            audit.log("CHANNEL_DELETE", app.getSlug() + " 删除渠道 " + name + (testers > 0 ? "，移除 " + testers + " 台测试设备" : ""));
            return "已删除渠道 " + name + "。还在用这个渠道的 App 检查更新时会拿到「没有更新」";
        });
    }

    // ==================== 令牌 ====================

    /** 令牌明文只显示这一次。scope=admin 是管理令牌（在系统配置页），否则是当前 App 的发布令牌 */
    @PostMapping("/apps/token")
    public String token(@RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "publish") String scope,
                        HttpSession session, RedirectAttributes ra) {
        boolean admin = "admin".equals(scope);
        OtaApp app = admin ? null : shell.current(session);
        if (admin && !access.owner()) return denied(ra, "只有超级管理员能新建管理令牌", "/admin");
        if (!admin && app == null) return "redirect:/admin/apps";
        if (!admin && !access.can(app, AppPerm.RELEASE)) return denied(ra, "没有「发布版本」权限，不能新建发布令牌", "/admin/app#tokens");
        String n = name.isBlank() ? (admin ? "管理令牌" : "发布令牌") : name.trim();
        TokenService.Issued issued = tokens.issue(n.length() > 100 ? n.substring(0, 100) : n,
                admin ? ApiToken.SCOPE_ADMIN : ApiToken.SCOPE_PUBLISH, admin ? null : app.getId());
        audit.log(admin ? null : app.getId(), "TOKEN_CREATE", (admin ? "新建管理令牌 " : app.getSlug() + " 新建发布令牌 ") + n);
        ra.addFlashAttribute("newToken", issued.plain());
        ra.addFlashAttribute("newTokenName", n);
        ra.addFlashAttribute("newTokenScope", admin ? "admin" : "publish");
        ra.addFlashAttribute("newTokenServer", props.baseUrl());
        ra.addFlashAttribute("newTokenApp", admin ? null : app.getSlug());
        return admin ? "redirect:/admin/settings#tokens" : "redirect:/admin/app#tokens";
    }

    @PostMapping("/apps/token/{id}/revoke")
    public String revoke(@PathVariable Long id, RedirectAttributes ra) {
        ApiToken found = tokens.find(id).orElse(null);
        boolean admin = found == null || ApiToken.SCOPE_ADMIN.equals(found.getScope());
        if (found != null) {
            if (admin && !access.owner()) return denied(ra, "只有超级管理员能撤销管理令牌", "/admin");
            if (!admin) {
                OtaApp owner = apps.findById(found.getAppId()).orElse(null);
                if (owner == null || !access.can(owner, AppPerm.RELEASE)) return denied(ra, "没有「发布版本」权限，不能撤销这个令牌", "/admin/app#tokens");
            }
        }
        try {
            ApiToken t = tokens.revoke(id);
            audit.log(admin ? null : t.getAppId(), "TOKEN_REVOKE", "撤销令牌 " + t.getName() + "（" + t.getScope() + "）");
            ra.addFlashAttribute("msg", "已撤销令牌 " + t.getName() + "，用它的命令行和 CI 会立即失效");
        } catch (IllegalArgumentException e) {
            admin = true;
            ra.addFlashAttribute("error", e.getMessage());
        }
        return admin ? "redirect:/admin/settings#tokens" : "redirect:/admin/app#tokens";
    }

    // ==================== 口令接口、Webhook ====================

    @PostMapping("/apps/codes-key/rotate")
    public String rotateCodeKey(HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "codes-api", app -> {
            apps.rotateCodeSecret(app);
            audit.log("APP_UPDATE", app.getSlug() + " 更换口令接口密钥");
            return "已更换口令接口密钥。用旧密钥签名的请求会被拒绝，App 要用新密钥重新发版";
        });
    }

    @PostMapping("/apps/webhook")
    public String webhook(@RequestParam(defaultValue = "") String url, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "webhook", app -> {
            apps.setWebhook(app, url);
            audit.log("WEBHOOK_UPDATE", app.getSlug() + (url.isBlank() ? " 关闭 Webhook" : " Webhook 改为 " + url.trim()));
            return url.isBlank() ? "已关闭 Webhook" : "已保存 Webhook 地址";
        });
    }

    @PostMapping("/apps/webhook/test")
    public String webhookTest(HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", app.getSlug());
        data.put("message", "这是一条测试通知");
        String result = webhooks.sendNow(app, "ping", data);
        ra.addFlashAttribute(result.startsWith("HTTP 2") ? "msg" : "error", "测试通知：" + result);
        return "redirect:/admin/app#webhook";
    }

    @PostMapping("/apps/webhook/rotate")
    public String webhookRotate(HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "webhook", app -> {
            apps.rotateWebhookSecret(app);
            audit.log("WEBHOOK_UPDATE", app.getSlug() + " 更换 Webhook 签名密钥");
            return "已更换 Webhook 签名密钥，接收方要同步更新";
        });
    }

    // ==================== 测试设备 ====================

    @PostMapping("/apps/testers")
    public String saveTester(@RequestParam String clientId, @RequestParam(defaultValue = "") String name,
                             @RequestParam String channel, @RequestParam(defaultValue = "") String rolloutFirst,
                             @RequestParam(defaultValue = "channels") String back, HttpSession session, RedirectAttributes ra) {
        boolean first = "true".equals(rolloutFirst) || "on".equals(rolloutFirst);
        String r = withApp(session, ra, "channels", app -> {
            TestDevice d = testDevices.save(app, clientId, name, channel, first);
            audit.log("TESTER_SAVE", app.getSlug() + " 测试设备 " + label(d) + " → 渠道 " + d.getChannel() + (first ? "，灰度白名单" : ""));
            return "已设为测试设备：" + label(d) + " 检查更新时用渠道 " + d.getChannel() + (first ? "，灰度没到 100% 时也先拿新版本" : "") + "，立即生效";
        });
        return "devices".equals(back) ? "redirect:/admin/devices" : r;
    }

    @PostMapping("/apps/testers/delete")
    public String deleteTester(@RequestParam Long id, @RequestParam(defaultValue = "channels") String back,
                               HttpSession session, RedirectAttributes ra) {
        String r = withApp(session, ra, "channels", app -> {
            TestDevice d = testDevices.remove(app, id);
            audit.log("TESTER_DELETE", app.getSlug() + " 移除测试设备 " + label(d));
            return "已移除测试设备 " + label(d) + "，它恢复用 App 里写的渠道";
        });
        return "devices".equals(back) ? "redirect:/admin/devices" : r;
    }

    private static String label(TestDevice d) {
        String id = d.getClientId().length() > 14 ? d.getClientId().substring(0, 8) + "…" : d.getClientId();
        return d.getName() == null ? id : d.getName() + "（" + id + "）";
    }

    // ==================== 群机器人 ====================

    @PostMapping("/apps/notify")
    public String notifyUrl(@RequestParam(defaultValue = "") String url, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "webhook", app -> {
            apps.setNotify(app, url);
            audit.log("NOTIFY_UPDATE", app.getSlug() + (url.isBlank() ? " 关闭群机器人" : " 群机器人改为 " + NotifyService.kind(url)));
            return url.isBlank() ? "已关闭群机器人通知" : "已保存" + NotifyService.kind(url) + "机器人，可以点「发送测试」看看";
        });
    }

    @PostMapping("/apps/notify/events")
    public String notifyEvents(@RequestParam(name = "event", required = false) List<String> events, HttpSession session, RedirectAttributes ra) {
        return withApp(session, ra, "webhook", app -> {
            notify.keepOnly(app.getId(), events);
            java.util.Set<String> off = notify.muted(app.getId());
            List<String> on = NotifyService.EVENTS.stream().filter(e -> !off.contains(e.id())).map(NotifyService.Event::name).toList();
            audit.log("NOTIFY_UPDATE", app.getSlug() + " 群机器人" + (on.isEmpty() ? "不推任何消息" : "只推：" + String.join("、", on)));
            return on.isEmpty() ? "已保存，群机器人暂时不推任何消息" : "已保存，群机器人只推：" + String.join("、", on);
        });
    }

    @PostMapping("/apps/notify/test")
    public String notifyTest(HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        String result = notify.sendNow(app, "这是一条测试消息，以后发布、回滚、切换 A/B 面、启动失败时会通知到这里");
        ra.addFlashAttribute(result.startsWith("成功") ? "msg" : "error", "测试消息：" + result);
        return "redirect:/admin/app#webhook";
    }

    // ==================== 备份、恢复、删除 ====================

    /** 下载当前 App 的加密备份。要先输自己的登录密码，防止别人借已登录的后台拿走私钥 */
    @PostMapping("/apps/backup")
    public ResponseEntity<byte[]> backup(@RequestParam String adminPassword, @RequestParam String backupPassword,
                                         @RequestParam String backupPassword2, HttpServletRequest req, HttpServletResponse res,
                                         HttpSession session) {
        OtaApp app = shell.current(session);
        String error;
        if (app == null) error = "还没有 App";
        else if (!adminPasswordOk(adminPassword)) error = "登录密码不对，没有生成备份";
        else if (!backupPassword.equals(backupPassword2)) error = "两次输入的备份密码不一样";
        else {
            try {
                byte[] file = backups.export(app, backupPassword);
                audit.log("APP_BACKUP", app.getSlug() + " 下载备份（签名密钥、口令接口密钥、Webhook、渠道、"
                        + codeRepo.count() + " 个口令）");
                String name = app.getSlug() + "-backup-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")
                        .format(LocalDateTime.now(ZoneId.of(props.getDisplayZone()))) + ".otabak";
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                        .header(HttpHeaders.CACHE_CONTROL, "no-store")
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .body(file);
            } catch (ApiException e) {
                error = e.getMessage();
            }
        }
        RequestContextUtils.getOutputFlashMap(req).put("error", error);
        RequestContextUtils.saveOutputFlashMap("/admin/app", req, res);
        return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, "/admin/app#backup").build();
    }

    /** 从备份恢复：App 不存在就新建；已存在时勾选了覆盖才会改回备份里的密钥 */
    @PostMapping("/apps/restore")
    public String restore(@RequestParam("file") MultipartFile file, @RequestParam String adminPassword,
                          @RequestParam String backupPassword, @RequestParam(defaultValue = "false") boolean overwrite,
                          @RequestParam(defaultValue = "apps") String from,
                          HttpSession session, RedirectAttributes ra) {
        String back = "app".equals(from) ? "redirect:/admin/app#backup" : "redirect:/admin/apps#restore";
        Restored r = restoreApp(file, adminPassword, backupPassword, overwrite, session);
        ra.addFlashAttribute(r.ok() ? "msg" : "error", r.msg());
        return r.ok() ? "redirect:/admin/app" : back;
    }

    /** 页面用 XHR 提交的：返回结果，页面自己提示、跳转 */
    @PostMapping(value = "/apps/restore", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public Map<String, Object> restoreAjax(@RequestParam("file") MultipartFile file, @RequestParam String adminPassword,
                                           @RequestParam String backupPassword, @RequestParam(defaultValue = "false") boolean overwrite,
                                           HttpSession session) {
        Restored r = restoreApp(file, adminPassword, backupPassword, overwrite, session);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", r.ok());
        m.put("msg", r.msg());
        if (r.ok()) m.put("redirect", "/admin/app");
        return m;
    }

    private record Restored(boolean ok, String msg) {}

    private Restored restoreApp(MultipartFile file, String adminPassword, String backupPassword, boolean overwrite, HttpSession session) {
        if (!adminPasswordOk(adminPassword)) return new Restored(false, "登录密码不对，没有恢复");
        try {
            if (file == null || file.isEmpty()) throw ApiException.badRequest("BAD_BACKUP", "请选择备份文件");
            if (file.getSize() > 50L * 1024 * 1024) throw ApiException.badRequest("BAD_BACKUP", "文件太大，不像是 App 备份");
            if (!access.owner()) {
                OtaApp target = apps.find(backups.slugOf(file.getBytes())).orElse(null);
                if (target == null || !access.can(target, AppPerm.SIGNING)) {
                    throw ApiException.forbidden("只能恢复你有「签名与备份」权限的 App；从备份新建 App 要超级管理员");
                }
            }
            BackupService.Restored r = backups.restore(file.getBytes(), backupPassword, overwrite);
            shell.use(session, r.app().getSlug());
            String detail = "签名密钥、口令接口密钥、Webhook 已恢复，渠道新增 " + r.channelsAdded() + " 个，口令新增 " + r.codesAdded() + " 个"
                    + (r.codesKept() > 0 ? "，" + r.codesKept() + " 个口令原来就有、保持不变" : "");
            audit.log("APP_RESTORE", (r.created() ? "从备份新建 " : "用备份恢复 ") + r.app().getSlug() + "：" + detail);
            return new Restored(true, (r.created() ? "已从备份新建 " : "已用备份恢复 ") + r.app().getName() + "：" + detail);
        } catch (ApiException | IllegalArgumentException e) {
            return new Restored(false, "没有恢复：" + e.getMessage());
        } catch (Exception e) {
            return new Restored(false, "没有恢复：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    /** 删除当前 App：要输入 App 标识和自己的登录密码 */
    @PostMapping("/apps/delete")
    public String delete(@RequestParam String confirmSlug, @RequestParam String adminPassword,
                         HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        if (!app.getSlug().equals(confirmSlug.trim())) {
            ra.addFlashAttribute("error", "输入的标识和 " + app.getSlug() + " 不一致，没有删除");
            return "redirect:/admin/app#delete";
        }
        if (!adminPasswordOk(adminPassword)) {
            ra.addFlashAttribute("error", "登录密码不对，没有删除");
            return "redirect:/admin/app#delete";
        }
        AppDeletionService.Deleted d = deletion.delete(app);
        audit.log("APP_DELETE", "删除 App " + app.getName() + "（" + app.getSlug() + "）：版本 " + d.releases() + " 个、激活记录 "
                + d.records() + " 条、发布令牌 " + d.tokens() + " 个");
        session.removeAttribute(AdminShell.SESSION_APP);
        ra.addFlashAttribute("msg", "已删除 " + app.getName() + "（" + app.getSlug() + "）及它的版本和激活记录，口令所有 App 通用，没有动。"
                + "版本文件可以在「系统配置 → 存储」里清理");
        return "redirect:/admin/apps";
    }

    // ==================== 内部 ====================

    private interface AppAction {
        String run(OtaApp app);
    }

    private String withApp(HttpSession session, RedirectAttributes ra, String tab, AppAction action) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        try {
            ra.addFlashAttribute("msg", action.run(app));
        } catch (ApiException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/app#" + tab;
    }

    /** 再确认一次当前登录管理员的密码，防止别人借已登录的后台做危险操作 */
    private boolean adminPasswordOk(String password) {
        CurrentAdmin me = access.current();
        return me != null && adminUsers.passwordMatches(me.id(), password);
    }

    private static String denied(RedirectAttributes ra, String why, String back) {
        ra.addFlashAttribute("error", why);
        return "redirect:" + back;
    }

    private static String text(MultipartFile f) throws Exception {
        if (f == null || f.isEmpty()) return null;
        if (f.getSize() > 32 * 1024) throw new IllegalArgumentException("文件过大，签名文件应是几 KB 的 .pem 文本文件");
        return new String(f.getBytes(), StandardCharsets.UTF_8);
    }
}
