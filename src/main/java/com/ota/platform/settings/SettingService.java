package com.ota.platform.settings;

import com.ota.platform.config.OtaProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** 系统配置：管理员账号密码、主题、侧栏、圆角、后台名称。读多写少，内存缓存。 */
@Service
public class SettingService {

    private static final Logger log = LoggerFactory.getLogger(SettingService.class);

    public static final String ADMIN_USERNAME = "admin.username";
    public static final String ADMIN_PASSWORD = "admin.password";
    public static final String UI_THEME = "ui.theme";
    public static final String UI_RADIUS = "ui.radius";
    public static final String UI_SIDEBAR = "ui.sidebar";
    public static final String UI_BRAND = "ui.brand";
    public static final String UI_WIDTH = "ui.width";
    public static final String UI_CARDS = "ui.cards";
    public static final String UI_BG = "ui.bg";
    public static final String UI_BUTTONS = "ui.buttons";
    public static final String UI_TABLES = "ui.tables";
    public static final String DEFAULT_BRAND = "OTA 管理后台";
    public static final int BRAND_MAX = 20;
    /** 换成主题色侧栏那一版：之前没人改过的「经典黑白」默认主题换成新的默认主题，只换一次 */
    private static final String UI_THEME_V2 = "ui.theme.v2";
    public static final String SEC_REMEMBER_DAYS = "security.remember_days";
    public static final String SEC_IDLE_MINUTES = "security.idle_minutes";
    public static final String SEC_REQUIRE_2FA = "security.require_2fa";
    public static final String SEC_LOCK_FAILS = "security.lock_fails";
    public static final String SEC_LOCK_MINUTES = "security.lock_minutes";
    public static final String SEC_MAX_DEVICES = "security.max_devices";
    public static final String LOG_KEEP_DAYS = "logs.keep_days";
    public static final String UI_LOGIN_TITLE = "ui.login_title";
    public static final String UI_LOGIN_TEXT = "ui.login_text";
    public static final String DEFAULT_LOGIN_TITLE = "热更新 · 口令 · 代理";
    public static final String DEFAULT_LOGIN_TEXT = "管理员与代理统一登录入口";

    /** 勾选「记住我」时保持登录的天数 */
    public static final java.util.List<Integer> REMEMBER_DAYS = java.util.List.of(1, 7, 30, 90);
    /** 不勾选「记住我」时，空闲多久自动退出（分钟） */
    public static final java.util.List<Integer> IDLE_MINUTES = java.util.List.of(30, 120, 480, 1440, 10080);
    /** 同一 IP 同一账号连续输错几次暂停登录 */
    public static final java.util.List<Integer> LOCK_FAILS = java.util.List.of(3, 5, 10);
    /** 暂停登录多久（分钟）；输错的次数也按这段时间来数 */
    public static final java.util.List<Integer> LOCK_MINUTES = java.util.List.of(5, 15, 30, 60);
    /** 每个管理员最多同时登录几台 */
    public static final java.util.List<Integer> MAX_DEVICES = java.util.List.of(1, 2, 3, 5, 10, 20);
    /** 操作日志保留多少天，0 是一直留着 */
    public static final java.util.List<Integer> LOG_KEEP = java.util.List.of(0, 90, 180, 365);

    /** 首次启动时的管理员账号，之后可在系统配置里修改 */
    private static final String INITIAL_ADMIN = "admin";
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9_.-]{3,32}$");

    private final SettingRepo repo;
    private final PasswordEncoder encoder;
    private final OtaProperties props;
    private final com.ota.platform.audit.OperationLogRepo logs;
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public SettingService(SettingRepo repo, PasswordEncoder encoder, OtaProperties props, com.ota.platform.audit.OperationLogRepo logs) {
        this.repo = repo;
        this.encoder = encoder;
        this.props = props;
        this.logs = logs;
    }

    @PostConstruct
    public void init() {
        repo.findAll().forEach(s -> {
            if (s.getValue() != null) cache.put(s.getKey(), s.getValue());
        });
        if (!cache.containsKey(UI_THEME_V2)) {
            if ("mono".equals(cache.get(UI_THEME)) && !logs.existsByAction("SETTINGS_APPEARANCE")) {
                log.info("外观没有改过，默认主题换成 {}", ThemeCatalog.theme(ThemeCatalog.DEFAULT_THEME).name());
                put(UI_THEME, ThemeCatalog.DEFAULT_THEME);
            }
            put(UI_THEME_V2, "1");
        }
        if (!cache.containsKey(UI_THEME)) put(UI_THEME, ThemeCatalog.DEFAULT_THEME);
        if (!cache.containsKey(UI_RADIUS)) put(UI_RADIUS, ThemeCatalog.DEFAULT_RADIUS);
    }

    public String adminUsername() { return cache.getOrDefault(ADMIN_USERNAME, INITIAL_ADMIN); }
    public String adminPasswordHash() { return cache.get(ADMIN_PASSWORD); }
    public String theme() { return ThemeCatalog.theme(cache.getOrDefault(UI_THEME, ThemeCatalog.DEFAULT_THEME)).id(); }
    public String radius() { return cache.getOrDefault(UI_RADIUS, ThemeCatalog.DEFAULT_RADIUS); }
    public String sidebar() { return ThemeCatalog.sidebarStyle(cache.getOrDefault(UI_SIDEBAR, ThemeCatalog.DEFAULT_SIDEBAR)).id(); }
    public String width() { return ThemeCatalog.look(ThemeCatalog.WIDTHS, cache.get(UI_WIDTH)).id(); }
    public String cards() { return ThemeCatalog.look(ThemeCatalog.CARDS, cache.get(UI_CARDS)).id(); }
    public String bg() { return ThemeCatalog.look(ThemeCatalog.BACKGROUNDS, cache.get(UI_BG)).id(); }
    public String buttons() { return ThemeCatalog.look(ThemeCatalog.BUTTONS, cache.get(UI_BUTTONS)).id(); }
    public String tables() { return ThemeCatalog.look(ThemeCatalog.TABLES, cache.get(UI_TABLES)).id(); }
    /** 后台名称：侧栏、登录页、浏览器标签上显示 */
    public String brandName() { return cache.getOrDefault(UI_BRAND, DEFAULT_BRAND); }

    public int rememberDays() { return pick(SEC_REMEMBER_DAYS, REMEMBER_DAYS, 30); }
    public int idleMinutes() { return pick(SEC_IDLE_MINUTES, IDLE_MINUTES, 1440); }
    public boolean require2fa() { return "true".equals(cache.get(SEC_REQUIRE_2FA)); }
    public int lockFails() { return pick(SEC_LOCK_FAILS, LOCK_FAILS, 5); }
    public int lockMinutes() { return pick(SEC_LOCK_MINUTES, LOCK_MINUTES, 15); }
    /** 后台没改过就用启动参数 ota.login.max-devices */
    public int maxDevices(int fallback) { return pick(SEC_MAX_DEVICES, MAX_DEVICES, fallback); }
    public int logKeepDays() { return pick(LOG_KEEP_DAYS, LOG_KEEP, 0); }
    /** 登录页左边的大标题和一句说明 */
    public String loginTitle() { return cache.getOrDefault(UI_LOGIN_TITLE, DEFAULT_LOGIN_TITLE); }
    public String loginText() { return cache.getOrDefault(UI_LOGIN_TEXT, DEFAULT_LOGIN_TEXT); }

    /** 不认识的值那项不改 */
    public synchronized void setProtection(int lockFails, int lockMinutes, int maxDevices, int logKeepDays) {
        if (LOCK_FAILS.contains(lockFails)) put(SEC_LOCK_FAILS, String.valueOf(lockFails));
        if (LOCK_MINUTES.contains(lockMinutes)) put(SEC_LOCK_MINUTES, String.valueOf(lockMinutes));
        if (MAX_DEVICES.contains(maxDevices)) put(SEC_MAX_DEVICES, String.valueOf(maxDevices));
        if (LOG_KEEP.contains(logKeepDays)) put(LOG_KEEP_DAYS, String.valueOf(logKeepDays));
    }

    /** 留空恢复默认；返回 null 是成功，否则是错误提示 */
    public synchronized String setLoginWords(String title, String text) {
        String a = title == null ? "" : title.strip().replaceAll("\\s+", " ");
        String b = text == null ? "" : text.strip().replaceAll("\\s+", " ");
        if (a.codePointCount(0, a.length()) > 30) return "登录页标题最多 30 个字";
        if (b.codePointCount(0, b.length()) > 60) return "登录页说明最多 60 个字";
        if ((a + b).chars().anyMatch(c -> c < 0x20 || c == '<' || c == '>')) return "登录页文字不能有 < > 和控制字符";
        put(UI_LOGIN_TITLE, a.isEmpty() ? DEFAULT_LOGIN_TITLE : a);
        put(UI_LOGIN_TEXT, b.isEmpty() ? DEFAULT_LOGIN_TEXT : b);
        return null;
    }

    public synchronized void setSecurity(int rememberDays, int idleMinutes, boolean require2fa) {
        if (REMEMBER_DAYS.contains(rememberDays)) put(SEC_REMEMBER_DAYS, String.valueOf(rememberDays));
        if (IDLE_MINUTES.contains(idleMinutes)) put(SEC_IDLE_MINUTES, String.valueOf(idleMinutes));
        put(SEC_REQUIRE_2FA, String.valueOf(require2fa));
    }

    private int pick(String key, java.util.List<Integer> allowed, int fallback) {
        try {
            int v = Integer.parseInt(cache.getOrDefault(key, ""));
            return allowed.contains(v) ? v : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** 一套外观：主题色、圆角、侧栏、页面宽度、卡片、背景、按钮、表格 */
    public record Appearance(String theme, String radius, String sidebar, String width, String cards, String bg, String buttons, String tables) {
        public static final java.util.List<String> KEYS = java.util.List.of("theme", "radius", "sidebar", "width", "cards", "bg", "buttons", "tables");

        public Map<String, String> toMap() {
            Map<String, String> m = new java.util.LinkedHashMap<>();
            m.put("theme", theme);
            m.put("radius", radius);
            m.put("sidebar", sidebar);
            m.put("width", width);
            m.put("cards", cards);
            m.put("bg", bg);
            m.put("buttons", buttons);
            m.put("tables", tables);
            return m;
        }
    }

    /** 默认外观（超级管理员选的）：代理后台、登录页，以及没自己选过外观的成员都用这一套 */
    public Appearance appearance() {
        return new Appearance(theme(), radius(), sidebar(), width(), cards(), bg(), buttons(), tables());
    }

    /** 在默认外观上换掉 picked 里认识的项；没选、不认识的项跟默认外观一样 */
    public Appearance appearance(Map<String, String> picked) {
        Appearance d = appearance();
        if (picked == null || picked.isEmpty()) return d;
        String side = picked.get("sidebar");
        return new Appearance(
                ThemeCatalog.isTheme(picked.get("theme")) ? ThemeCatalog.theme(picked.get("theme")).id() : d.theme(),
                ThemeCatalog.isRadius(picked.get("radius")) ? picked.get("radius") : d.radius(),
                ThemeCatalog.isSidebar(side) ? ThemeCatalog.sidebarStyle(side).id() : d.sidebar(),
                ThemeCatalog.isLook(ThemeCatalog.WIDTHS, picked.get("width")) ? picked.get("width") : d.width(),
                ThemeCatalog.isLook(ThemeCatalog.CARDS, picked.get("cards")) ? picked.get("cards") : d.cards(),
                ThemeCatalog.isLook(ThemeCatalog.BACKGROUNDS, picked.get("bg")) ? picked.get("bg") : d.bg(),
                ThemeCatalog.isLook(ThemeCatalog.BUTTONS, picked.get("buttons")) ? picked.get("buttons") : d.buttons(),
                ThemeCatalog.isLook(ThemeCatalog.TABLES, picked.get("tables")) ? picked.get("tables") : d.tables());
    }

    /** 装好时的样子，系统配置里「恢复默认」回到这一套 */
    public static Appearance factory() {
        return new Appearance(ThemeCatalog.DEFAULT_THEME, ThemeCatalog.DEFAULT_RADIUS, ThemeCatalog.DEFAULT_SIDEBAR, ThemeCatalog.DEFAULT_WIDTH,
                ThemeCatalog.DEFAULT_CARDS, ThemeCatalog.DEFAULT_BG, ThemeCatalog.DEFAULT_BUTTONS, ThemeCatalog.DEFAULT_TABLES);
    }

    public String themeCss() {
        return themeCss(appearance());
    }

    public String themeCss(Appearance a) {
        return ThemeCatalog.css(a.theme(), a.radius(), a.sidebar()) + ThemeCatalog.lookCss(a.width(), a.cards(), a.bg(), a.buttons(), a.tables());
    }

    /** 管理员账号是否被占用（代理不能用这个账号名） */
    public boolean isAdminUsername(String username) {
        return username != null && (username.equalsIgnoreCase(adminUsername()) || username.equalsIgnoreCase(INITIAL_ADMIN));
    }

    /** 修改管理员账号和/或密码，必须先验证当前密码。返回 null 表示成功，否则是错误提示。 */
    public synchronized String changeAdmin(String currentPassword, String newUsername, String newPassword,
                                           java.util.function.Predicate<String> usernameTakenByAgent) {
        if (currentPassword == null || !encoder.matches(currentPassword, adminPasswordHash())) {
            return "当前密码不正确";
        }
        String username = newUsername == null ? "" : newUsername.trim();
        if (!USERNAME.matcher(username).matches()) return "账号需为 3–32 位字母、数字或 _ . -";
        if (!username.equals(adminUsername()) && usernameTakenByAgent.test(username.toLowerCase())) {
            return "账号 " + username + " 已被代理使用";
        }
        boolean changePassword = newPassword != null && !newPassword.isEmpty();
        if (changePassword && newPassword.length() < 8) return "新密码至少 8 位";
        if (username.equals(adminUsername()) && !changePassword) return "账号和密码都没有变化";
        put(ADMIN_USERNAME, username);
        if (changePassword) put(ADMIN_PASSWORD, encoder.encode(newPassword));
        return null;
    }

    public synchronized void setAppearance(String theme, String radius, String sidebar) {
        if (ThemeCatalog.isTheme(theme)) put(UI_THEME, theme);
        if (ThemeCatalog.isRadius(radius)) put(UI_RADIUS, radius);
        if (ThemeCatalog.isSidebar(sidebar)) put(UI_SIDEBAR, sidebar);
    }

    /** 卡片、页面背景、按钮、表格的样式；没传或不认识的那项保持原样 */
    public synchronized void setLooks(String width, String cards, String bg, String buttons, String tables) {
        if (ThemeCatalog.isLook(ThemeCatalog.WIDTHS, width)) put(UI_WIDTH, width);
        if (ThemeCatalog.isLook(ThemeCatalog.CARDS, cards)) put(UI_CARDS, cards);
        if (ThemeCatalog.isLook(ThemeCatalog.BACKGROUNDS, bg)) put(UI_BG, bg);
        if (ThemeCatalog.isLook(ThemeCatalog.BUTTONS, buttons)) put(UI_BUTTONS, buttons);
        if (ThemeCatalog.isLook(ThemeCatalog.TABLES, tables)) put(UI_TABLES, tables);
    }

    /** 改后台名称；留空恢复默认。返回 null 表示成功，否则是错误提示 */
    public synchronized String setBrand(String name) {
        String v = name == null ? "" : name.strip().replaceAll("\\s+", " ");
        if (v.isEmpty()) v = DEFAULT_BRAND;
        if (v.codePointCount(0, v.length()) > BRAND_MAX) return "后台名称最多 " + BRAND_MAX + " 个字";
        if (v.chars().anyMatch(c -> c < 0x20 || c == '<' || c == '>')) return "后台名称不能有 < > 和控制字符";
        put(UI_BRAND, v);
        return null;
    }

    private void put(String key, String value) {
        repo.save(new Setting(key, value));
        cache.put(key, value);
    }

    /** 新功能自己的设置项（自动备份、演示账号……）：没设过就用默认值 */
    public String get(String key, String def) {
        return cache.getOrDefault(key, def);
    }

    public int getInt(String key, int def) {
        try {
            return Integer.parseInt(cache.getOrDefault(key, String.valueOf(def)));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    public synchronized void set(String key, String value) {
        put(key, value);
    }

}
