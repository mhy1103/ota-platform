package com.ota.platform.web.admin;

import com.ota.platform.web.AdminUi;
import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AdminUserService;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.audit.AuditService;
import com.ota.platform.auth.TokenService;
import com.ota.platform.common.ApiException;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.install.InstallConfig;
import com.ota.platform.install.InstallFilters;
import com.ota.platform.install.InstallService;
import com.ota.platform.settings.SettingService;
import com.ota.platform.settings.ThemeCatalog;
import com.ota.platform.storage.BlobStore;
import com.ota.platform.storage.StorageService;
import com.ota.platform.storage.StorageSwitcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 系统配置：外观、管理令牌、存储用量和清理，以及只读的运行配置（只有超级管理员能进） */
@Controller
@RequestMapping("/admin/settings")
public class AdminSettingsController {

    private static final Logger log = LoggerFactory.getLogger(AdminSettingsController.class);
    private final org.springframework.beans.factory.ObjectProvider<com.ota.platform.demo.DemoSandbox> demo;
    private final com.ota.platform.backup.ProjectBackupService backups;
    private final SettingService settings;
    private final AgentRepo agentRepo;
    private final AuditService audit;
    private final AdminShell shell;
    private final OtaProperties props;
    private final BlobStore blobs;
    private final TokenService tokens;
    private final StorageService storage;
    private final ObjectProvider<BuildProperties> build;
    private final AdminUi ui;
    private final InstallService install;
    private final AdminUserService users;
    private final AccessService access;
    private final StorageSwitcher switcher;

    private final com.ota.platform.security.AdminLogins logins;
    private final com.ota.platform.audit.LogRetention retention;
    private final com.ota.platform.web.Prefs prefs;
    private final com.ota.platform.web.MetricsService metrics;

    public AdminSettingsController(SettingService settings, AgentRepo agentRepo, AuditService audit, AdminShell shell,
                                   OtaProperties props, BlobStore blobs, TokenService tokens, StorageService storage,
                                   ObjectProvider<BuildProperties> build, AdminUi ui, InstallService install,
                                   AdminUserService users, AccessService access, StorageSwitcher switcher,
                                   com.ota.platform.backup.ProjectBackupService backups,
                                   org.springframework.beans.factory.ObjectProvider<com.ota.platform.demo.DemoSandbox> demo,
                                   com.ota.platform.security.AdminLogins logins,
                                   com.ota.platform.audit.LogRetention retention,
                                   com.ota.platform.web.Prefs prefs,
                                   com.ota.platform.web.MetricsService metrics) {
        this.metrics = metrics;
        this.prefs = prefs;
        this.logins = logins;
        this.retention = retention;
        this.demo = demo;
        this.backups = backups;
        this.switcher = switcher;
        this.settings = settings;
        this.agentRepo = agentRepo;
        this.audit = audit;
        this.shell = shell;
        this.props = props;
        this.blobs = blobs;
        this.tokens = tokens;
        this.storage = storage;
        this.build = build;
        this.ui = ui;
        this.install = install;
        this.users = users;
        this.access = access;
    }

    /** 外观要的选项和当前选择。def 是「恢复默认」回到的那一套：超级管理员是装好时的样子，成员是超级管理员选的默认外观 */
    private void appearanceModel(Model model, SettingService.Appearance current, SettingService.Appearance def) {
        model.addAttribute("themes", ThemeCatalog.THEMES);
        model.addAttribute("radii", ThemeCatalog.RADII);
        model.addAttribute("sidebars", ThemeCatalog.SIDEBARS);
        model.addAttribute("lookWidths", ThemeCatalog.WIDTHS);
        model.addAttribute("lookCards", ThemeCatalog.CARDS);
        model.addAttribute("lookBgs", ThemeCatalog.BACKGROUNDS);
        model.addAttribute("lookButtons", ThemeCatalog.BUTTONS);
        model.addAttribute("lookTables", ThemeCatalog.TABLES);
        model.addAttribute("presets", ThemeCatalog.PRESETS);
        model.addAttribute("currentTheme", current.theme());
        model.addAttribute("currentRadius", current.radius());
        model.addAttribute("currentSidebar", current.sidebar());
        model.addAttribute("currentWidth", current.width());
        model.addAttribute("currentCards", current.cards());
        model.addAttribute("currentBg", current.bg());
        model.addAttribute("currentButtons", current.buttons());
        model.addAttribute("currentTables", current.tables());
        try {
            model.addAttribute("defaultLook", new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(def.toMap()));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            model.addAttribute("defaultLook", "{}");
        }
    }

    @GetMapping
    public String page(Model model, HttpSession session) {
        shell.populate(model, session);
        // 成员只能进外观：自己选一套，只对自己生效
        if (!access.owner()) {
            Map<String, String> mine = users.find(access.current().username()).map(u -> prefs.look(u.getPrefs())).orElse(null);
            appearanceModel(model, settings.appearance(mine), settings.appearance());
            model.addAttribute("ownLook", mine != null);
            return "admin/settings";
        }
        appearanceModel(model, settings.appearance(), SettingService.factory());
        model.addAttribute("adminTokens", tokens.adminTokens());
        model.addAttribute("usage", storage.usage());
        BuildProperties b = build.getIfAvailable();
        model.addAttribute("version", b != null ? b.getVersion() : "开发版");
        model.addAttribute("builtAt", b != null && b.getTime() != null ? ui.full(b.getTime()) : null);
        model.addAttribute("publicUrl", props.baseUrl());
        model.addAttribute("dataDir", Path.of(props.getDataDir()).toAbsolutePath().normalize().toString());
        model.addAttribute("storage", blobs.describe());
        Runtime rt = Runtime.getRuntime();
        long memUsed = rt.totalMemory() - rt.freeMemory(), memMax = rt.maxMemory();
        long diskFree = install.freeDiskBytes(), diskTotal = install.dataDir().toFile().getTotalSpace();
        String up = uptime(java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
        String javaInfo = System.getProperty("java.version") + " · " + System.getProperty("os.name") + " " + System.getProperty("os.arch");
        model.addAttribute("uptime", up);
        model.addAttribute("memUsed", memUsed);
        model.addAttribute("memMax", memMax);
        model.addAttribute("diskFree", diskFree);
        model.addAttribute("diskTotal", diskTotal);
        model.addAttribute("diskLow", diskFree >= 0 && diskFree < 2L * 1024 * 1024 * 1024);
        model.addAttribute("javaInfo", javaInfo);
        Object builtAt = model.getAttribute("builtAt");
        model.addAttribute("diagText", String.join("\n",
                "OTA 平台 " + model.getAttribute("version") + (builtAt == null ? "" : "（构建于 " + builtAt + "）"),
                "对外地址：" + props.baseUrl(),
                "数据目录：" + model.getAttribute("dataDir"),
                "文件存储：" + blobs.describe(),
                "数据库：" + install.effectiveDatabase(),
                "Java：" + javaInfo,
                "已运行：" + up,
                "内存：已用 " + size(memUsed) + " / 最多 " + size(memMax),
                "磁盘：剩余 " + size(diskFree) + " / 共 " + size(diskTotal)));
        model.addAttribute("patchEnabled", props.getPatch().isEnabled());
        model.addAttribute("metricsOn", metrics.enabled());
        model.addAttribute("metricsPrefix", metrics.prefix());
        model.addAttribute("metricsCreated", metrics.createdAt());
        model.addAttribute("metricsLast", metrics.lastScrape());
        model.addAttribute("metricsUrl", props.baseUrl() + "/api/metrics");
        model.addAttribute("inst", install.snapshot());
        model.addAttribute("publicUrlFromStartup", install.fromStartup("ota.public-url"));
        model.addAttribute("publicUrlFix", install.publicUrlFix());
        OtaProperties.S3 s3 = props.getStorage().getS3();
        Map<String, Object> s3Form = new LinkedHashMap<>();
        s3Form.put("endpoint", s3.getEndpoint());
        s3Form.put("region", s3.getRegion());
        s3Form.put("bucket", s3.getBucket());
        s3Form.put("accessKey", s3.getAccessKey());
        s3Form.put("hasSecret", props.getStorage().isS3() && s3.getSecretKey() != null && !s3.getSecretKey().isBlank());
        s3Form.put("pathStyle", s3.isPathStyle());
        s3Form.put("prefix", s3.getPrefix());
        s3Form.put("publicUrl", s3.getPublicUrl());
        model.addAttribute("storageS3", props.getStorage().isS3());
        model.addAttribute("s3Form", s3Form);
        model.addAttribute("storageLocked", switcher.lockedByStartup());
        model.addAttribute("storageBusy", "running".equals(switcher.status().state()));
        model.addAttribute("storageLast", switcher.status().needsAttention(Duration.ofHours(24)));
        String at = install.snapshot().installedAt;
        model.addAttribute("installedAt", at == null ? null : ui.full(Instant.parse(at)));
        model.addAttribute("effectiveDb", install.effectiveDatabase());
        model.addAttribute("displayZone", props.getDisplayZone());
        model.addAttribute("rememberDays", settings.rememberDays());
        model.addAttribute("idleMinutes", settings.idleMinutes());
        model.addAttribute("require2fa", settings.require2fa());
        model.addAttribute("rememberOptions", SettingService.REMEMBER_DAYS);
        model.addAttribute("idleOptions", SettingService.IDLE_MINUTES);
        model.addAttribute("lockFails", settings.lockFails());
        model.addAttribute("lockMinutes", settings.lockMinutes());
        model.addAttribute("lockPerIp", com.ota.platform.config.LoginGuard.perIp(settings.lockFails()));
        model.addAttribute("lockFailOptions", SettingService.LOCK_FAILS);
        model.addAttribute("lockMinuteOptions", SettingService.LOCK_MINUTES);
        java.util.TreeSet<Integer> deviceOptions = new java.util.TreeSet<>(SettingService.MAX_DEVICES);
        deviceOptions.add(logins.maxDevices());
        model.addAttribute("deviceOptions", new ArrayList<>(deviceOptions));
        model.addAttribute("logKeepDays", settings.logKeepDays());
        model.addAttribute("logKeepOptions", SettingService.LOG_KEEP);
        model.addAttribute("loginTitle", settings.loginTitle());
        model.addAttribute("loginText", settings.loginText());
        model.addAttribute("without2fa", users.all().stream().filter(u -> u.isEnabled() && !u.isTotpEnabled()).toList());
        com.ota.platform.demo.DemoSandbox sandbox = demo.getIfAvailable();
        if (sandbox != null) {
            model.addAttribute("demoEnabled", sandbox.enabled());
            model.addAttribute("demoUser", sandbox.username());
            model.addAttribute("demoRunning", sandbox.running());
            Instant created = sandbox.createdAt();
            model.addAttribute("demoCreatedAt", created == null ? null : ui.full(created));
        }
        model.addAttribute("backupStored", backups.stored());
        model.addAttribute("maxDevices", logins.maxDevices());
        model.addAttribute("backupAuto", "true".equals(settings.get(com.ota.platform.backup.ProjectBackupService.AUTO, "false")));
        model.addAttribute("backupKeep", settings.getInt(com.ota.platform.backup.ProjectBackupService.KEEP, 7));
        model.addAttribute("backupHour", settings.getInt(com.ota.platform.backup.ProjectBackupService.HOUR, 3));
        model.addAttribute("backupLast", backups.lastBackup());
        com.ota.platform.backup.ProjectBackupService.Sizes sizes = backups.sizes();
        model.addAttribute("backupSize", sizes.total());
        model.addAttribute("backupDbSize", sizes.db());
        model.addAttribute("backupFilesSize", sizes.files());
        model.addAttribute("backupH2", backups.isH2());
        model.addAttribute("backupPending", backups.pending());
        com.fasterxml.jackson.databind.JsonNode last = backups.lastRestore();
        if (last != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("ok", last.path("ok").asBoolean());
            r.put("at", last.path("restoredAt").asText(""));
            r.put("from", last.path("backupCreatedAt").asText(""));
            r.put("before", last.path("before").asText(""));
            r.put("error", last.path("error").asText(""));
            model.addAttribute("backupRestored", r);
        }
        return "admin/settings";
    }

    static String uptime(long ms) {
        long min = ms / 60_000, d = min / 1440, h = min % 1440 / 60, m = min % 60;
        if (d > 0) return d + " 天 " + h + " 小时";
        if (h > 0) return h + " 小时 " + m + " 分钟";
        return Math.max(1, m) + " 分钟";
    }

    static String size(long bytes) {
        if (bytes < 1024) return Math.max(0, bytes) + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = bytes;
        int i = -1;
        do {
            v /= 1024;
            i++;
        } while (v >= 1024 && i < units.length - 1);
        return (v >= 100 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v)) + " " + units[i];
    }

    /** 登录安全：「记住我」保持几天、不勾时空闲多久退出、是否要求两步验证、输错暂停、同时登录台数、日志保留；没传的那项不变 */
    @PostMapping("/security")
    public String security(@RequestParam(defaultValue = "30") int rememberDays, @RequestParam(defaultValue = "1440") int idleMinutes,
                           @RequestParam(defaultValue = "false") boolean require2fa,
                           @RequestParam(defaultValue = "0") int lockFails, @RequestParam(defaultValue = "0") int lockMinutes,
                           @RequestParam(defaultValue = "0") int maxDevices, @RequestParam(defaultValue = "-1") int logKeepDays,
                           RedirectAttributes ra) {
        if (require2fa && !settings.require2fa() && !users.require(access.current().id()).isTotpEnabled()) {
            ra.addFlashAttribute("error", "请先在「我的账号 → 登录与安全」里开启你自己的两步验证，再要求所有人开启");
            return "redirect:/admin/settings#security";
        }
        settings.setSecurity(rememberDays, idleMinutes, require2fa);
        // 没改就不存，继续跟着启动参数
        settings.setProtection(lockFails, lockMinutes, maxDevices == logins.maxDevices() ? 0 : maxDevices, logKeepDays);
        int cleaned = retention.clean();
        int keep = settings.logKeepDays();
        audit.log("SECURITY_SETTINGS", "登录安全：记住我保持 " + settings.rememberDays() + " 天，不记住时空闲 "
                + AdminUi.minutes(settings.idleMinutes()) + " 退出，" + (settings.require2fa() ? "要求" : "不要求") + "所有管理员开启两步验证，"
                + "密码输错 " + settings.lockFails() + " 次暂停登录 " + settings.lockMinutes() + " 分钟，每人最多同时登录 " + logins.maxDevices()
                + " 台，操作日志" + (keep == 0 ? "一直保留" : "保留 " + keep + " 天"));
        ra.addFlashAttribute("msg", "已保存，之后的登录按新设置生效" + (cleaned > 0 ? "；清掉了 " + cleaned + " 条超过 " + keep + " 天的操作日志" : ""));
        return "redirect:/admin/settings#security";
    }

    /**
     * 访问域名：每行一个域名、用途（both / admin / api）、协议，开关决定是否只允许这些域名访问。
     * 直接读原始参数：交给 Spring 绑定成列表时，带逗号的单个值会被拆开，几列就对不齐了
     */
    @PostMapping("/domains")
    public String domains(@RequestParam(defaultValue = "false") boolean restrict, HttpServletRequest req, RedirectAttributes ra) {
        String[] hosts = req.getParameterValues("host"), uses = req.getParameterValues("use"), schemes = req.getParameterValues("scheme");
        List<InstallService.DomainForm> forms = new ArrayList<>();
        for (int i = 0; hosts != null && i < hosts.length; i++) {
            String use = uses != null && i < uses.length ? uses[i] : "both";
            boolean https = schemes == null || i >= schemes.length || !"http".equals(schemes[i]);
            forms.add(new InstallService.DomainForm(hosts[i], !"api".equals(use), !"admin".equals(use), https));
        }
        try {
            install.updateDomains(restrict, forms, req.getParameter("publicUrl"), req.getServerName());
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/settings#install";
        }
        InstallConfig c = install.snapshot();
        String list = c.domains.isEmpty() ? "未绑定域名" : c.domains.stream().map(d -> d.host).collect(Collectors.joining("、"));
        audit.log("DOMAINS_UPDATE", "访问域名：" + list + "，" + (c.restrictDomains ? "只允许这些域名访问" : "不限制访问域名")
                + (c.publicUrl != null ? "，接口对外地址 " + c.publicUrl : ""));
        ra.addFlashAttribute("msg", install.fromStartup("ota.public-url")
                ? "访问域名已保存。接口对外地址以启动参数 --ota.public-url 为准，去掉那个参数后这里填的才生效"
                : "访问域名和接口对外地址已保存，马上生效");
        return "redirect:/admin/settings#install";
    }

    /** 对外地址漏了端口时一键补上（建议值由服务端按绑定的接口域名算） */
    @PostMapping("/public-url")
    public String publicUrl(@RequestParam(defaultValue = "") String publicUrl, RedirectAttributes ra) {
        try {
            install.updatePublicUrl(publicUrl);
            audit.log("DOMAINS_UPDATE", "接口对外地址改为 " + publicUrl.trim());
            ra.addFlashAttribute("msg", install.fromStartup("ota.public-url")
                    ? "已保存。接口对外地址以启动参数 --ota.public-url 为准，去掉那个参数后这里的才生效"
                    : "接口对外地址已改为 " + publicUrl.trim() + "，API 文档、接入配置和 App 下载文件的地址马上跟着变");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/settings#install";
    }

    /** 文件存储：先试连接，再开始切换（后台复制文件），页面轮询进度 */
    @PostMapping("/storage/test")
    @ResponseBody
    public Map<String, Object> storageTest(HttpServletRequest req) {
        try {
            return Map.of("ok", true, "msg", switcher.test(storageForm(req)));
        } catch (ApiException e) {
            return Map.of("ok", false, "msg", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("测试文件存储失败", e);
            return Map.of("ok", false, "msg", "测试失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    @PostMapping("/storage/switch")
    @ResponseBody
    public Map<String, Object> storageSwitch(HttpServletRequest req) {
        try {
            switcher.start(storageForm(req), access.current().username());
            return Map.of("ok", true, "msg", "开始切换");
        } catch (ApiException e) {
            return Map.of("ok", false, "msg", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("切换文件存储失败", e);
            return Map.of("ok", false, "msg", "切换失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    /** 原来的存储连不上，管理员确认清空新存储里没有文件的版本和安装包，然后换过去 */
    @PostMapping("/storage/clear")
    @ResponseBody
    public Map<String, Object> storageClear() {
        try {
            switcher.clearAndSwitch(access.current().username());
            return Map.of("ok", true, "msg", "开始切换");
        } catch (ApiException e) {
            return Map.of("ok", false, "msg", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("切换文件存储失败", e);
            return Map.of("ok", false, "msg", "切换失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        }
    }

    @GetMapping("/storage/status")
    @ResponseBody
    public StorageSwitcher.Status storageStatus() {
        return switcher.status();
    }

    private static StorageSwitcher.Form storageForm(HttpServletRequest req) {
        return new StorageSwitcher.Form(req.getParameter("type"), req.getParameter("endpoint"), req.getParameter("region"),
                req.getParameter("bucket"), req.getParameter("accessKey"), req.getParameter("secretKey"),
                "true".equals(req.getParameter("pathStyle")), req.getParameter("prefix"), req.getParameter("publicUrl"));
    }

    /** 重新打开安装引导：要再输一次自己的密码，30 分钟内有效 */
    @PostMapping("/reinstall")
    public String reinstall(@RequestParam(defaultValue = "") String password, HttpSession session, RedirectAttributes ra) {
        if (!users.passwordMatches(access.current().id(), password)) {
            ra.addFlashAttribute("error", "密码不正确，没有打开安装引导");
            return "redirect:/admin/settings#install";
        }
        session.setAttribute(InstallFilters.REOPEN, Instant.now());
        audit.log("INSTALL_REOPEN", access.current().username() + " 重新打开安装引导");
        return "redirect:/install";
    }

    /** 清理没有版本再用的文件。24 小时内登记的不动：可能正在上传、还没登记版本 */
    @PostMapping("/storage/clean")
    public String cleanStorage(RedirectAttributes ra) {
        StorageService.Cleaned c = storage.clean(Duration.ofHours(24));
        audit.log("STORAGE_CLEAN", "清理存储：文件 " + c.files() + " 个（" + ui.bytes(c.bytes()) + "）、补丁记录 " + c.patches() + " 条");
        ra.addFlashAttribute("msg", c.files() == 0 && c.patches() == 0 ? "没有可以清理的文件"
                : "已清理 " + c.files() + " 个文件，释放 " + ui.bytes(c.bytes()) + (c.patches() > 0 ? "，删除 " + c.patches() + " 条用不上的补丁记录" : ""));
        return "redirect:/admin/settings#storage";
    }

    /** 监控指标：生成（或重新生成）监控令牌，只显示这一次 */
    @PostMapping("/metrics/token")
    public String metricsToken(RedirectAttributes ra) {
        boolean had = metrics.enabled();
        String token = metrics.rotate();
        audit.log("METRICS_TOKEN", had ? "重新生成了监控令牌，旧令牌已失效" : "生成了监控令牌，开放了监控指标 /api/metrics");
        java.net.URI base = java.net.URI.create(props.baseUrl());
        String path = base.getPath() == null ? "" : base.getPath().replaceAll("/+$", "");
        ra.addFlashAttribute("newToken", token);
        ra.addFlashAttribute("newTokenName", "监控令牌");
        ra.addFlashAttribute("newTokenScope", "metrics");
        ra.addFlashAttribute("newTokenServer", props.baseUrl());
        ra.addFlashAttribute("newTokenScheme", base.getScheme() == null ? "https" : base.getScheme());
        ra.addFlashAttribute("newTokenHost", base.getAuthority() == null ? "ota.example.com" : base.getAuthority());
        ra.addFlashAttribute("newTokenPath", path + "/api/metrics");
        return "redirect:/admin/settings#system";
    }

    @PostMapping("/metrics/off")
    public String metricsOff(RedirectAttributes ra) {
        metrics.disable();
        audit.log("METRICS_TOKEN", "关闭了监控指标，监控令牌已失效");
        ra.addFlashAttribute("msg", "已关闭监控指标，/api/metrics 不再返回数据");
        return "redirect:/admin/settings#system";
    }

    /** 演示账号：开关、账号名、密码（不填不改） */
    @PostMapping("/demo")
    public String demo(@RequestParam(defaultValue = "false") boolean enabled, @RequestParam String username,
                       @RequestParam(defaultValue = "") String password, RedirectAttributes ra) {
        com.ota.platform.demo.DemoSandbox sandbox = demo.getIfAvailable();
        if (sandbox == null) return "redirect:/admin/settings#security";
        String u = username == null ? "" : username.trim().toLowerCase(java.util.Locale.ROOT);
        String error = null;
        if (!u.matches("[a-z0-9_.-]{3,32}")) error = "演示账号用 3–32 位小写字母、数字、_ . -";
        else if (users.all().stream().anyMatch(a -> a.getUsername().equalsIgnoreCase(u)) || agentRepo.findByUsername(u).isPresent()) {
            error = "这个账号名已经被管理员或代理用了，换一个";
        } else if (!password.isEmpty() && password.length() < 6) error = "演示密码至少 6 位";
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/admin/settings#security";
        }
        sandbox.configure(enabled, u, password);
        audit.log("SETTINGS_DEMO", (enabled ? "开启了演示账号 " + u : "关闭了演示账号") + (password.isEmpty() ? "" : " · 改了演示密码"));
        ra.addFlashAttribute("msg", enabled ? "已保存演示账号 " + u + (password.isEmpty() ? "" : "，密码已更新") : "已关闭演示账号");
        return "redirect:/admin/settings#security";
    }

    @PostMapping("/demo/reset")
    public String demoReset(RedirectAttributes ra) {
        com.ota.platform.demo.DemoSandbox sandbox = demo.getIfAvailable();
        if (sandbox != null) {
            sandbox.reset();
            audit.log("DEMO_RESET", "恢复演示数据");
            ra.addFlashAttribute("msg", "演示数据会恢复成默认的，下次有人用演示账号登录时生效");
        }
        return "redirect:/admin/settings#security";
    }

    @PostMapping("/appearance")
    public String appearance(@RequestParam String theme, @RequestParam String radius,
                             @RequestParam(required = false) String sidebar, @RequestParam(required = false) String brand,
                             @RequestParam(required = false) String width,
                             @RequestParam(required = false) String cards, @RequestParam(required = false) String bg,
                             @RequestParam(required = false) String buttons, @RequestParam(required = false) String tables,
                             @RequestParam(required = false) String loginTitle, @RequestParam(required = false) String loginText,
                             @RequestParam(defaultValue = "0") String follow,
                             RedirectAttributes ra) {
        if (!access.owner()) {
            Map<String, String> picked = new LinkedHashMap<>();
            String[] values = {theme, radius, sidebar, width, cards, bg, buttons, tables};
            for (int i = 0; i < values.length; i++) if (values[i] != null) picked.put(SettingService.Appearance.KEYS.get(i), values[i]);
            SettingService.Appearance mine = settings.appearance(picked);
            // 点了「跟默认一样」，或者选得和默认外观一模一样：不单独存，以后跟着超级管理员改
            boolean same = "1".equals(follow) || mine.equals(settings.appearance());
            var me = access.current();
            prefs.updateAdmin(me.id(), s -> prefs.withLook(s, same ? null : mine.toMap()));
            prefs.forgetLook(me.username());
            ra.addFlashAttribute("msg", same ? "已改回默认外观，以后跟着超级管理员的设置走" : "已保存你的外观：" + describe(mine) + "，只对你自己生效");
            return "redirect:/admin/settings";
        }
        if (brand != null) {
            String err = settings.setBrand(brand);
            if (err != null) {
                ra.addFlashAttribute("error", err);
                return "redirect:/admin/settings";
            }
        }
        if (loginTitle != null || loginText != null) {
            String err = settings.setLoginWords(loginTitle == null ? settings.loginTitle() : loginTitle,
                    loginText == null ? settings.loginText() : loginText);
            if (err != null) {
                ra.addFlashAttribute("error", err);
                return "redirect:/admin/settings";
            }
        }
        settings.setAppearance(theme, radius, sidebar);
        settings.setLooks(width, cards, bg, buttons, tables);
        String looks = describe(settings.appearance());
        audit.log("SETTINGS_APPEARANCE", "外观改为 " + looks);
        ra.addFlashAttribute("msg", "已保存默认外观：" + looks);
        return "redirect:/admin/settings";
    }

    private static String describe(SettingService.Appearance a) {
        return ThemeCatalog.theme(a.theme()).name() + " · " + ThemeCatalog.radius(a.radius()).name() + " · "
                + ThemeCatalog.sidebarStyle(a.sidebar()).name() + "侧栏 · "
                + ThemeCatalog.look(ThemeCatalog.WIDTHS, a.width()).name() + "版面 · "
                + ThemeCatalog.look(ThemeCatalog.CARDS, a.cards()).name() + "卡片 · "
                + ThemeCatalog.look(ThemeCatalog.BACKGROUNDS, a.bg()).name() + "背景 · "
                + ThemeCatalog.look(ThemeCatalog.BUTTONS, a.buttons()).name() + "按钮 · "
                + ThemeCatalog.look(ThemeCatalog.TABLES, a.tables()).name() + "表格";
    }
}
