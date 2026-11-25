package com.ota.platform.install;

import com.ota.platform.OtaPlatformApplication;
import com.ota.platform.admin.AdminUser;
import com.ota.platform.admin.AdminUserRepo;
import com.ota.platform.common.ApiException;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.settings.SettingRepo;
import com.ota.platform.settings.SettingService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.CommandLinePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 安装状态和安装引导的后端：没装过就引导安装；从旧版本升级、或者用环境变量指定了管理员密码的，启动时自动算作已安装。
 */
@Service
public class InstallService {

    private static final Logger log = LoggerFactory.getLogger(InstallService.class);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.\\-]{3,32}");
    private static final Pattern HOST = Pattern.compile("(?i)^(\\*\\.)?[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*(:\\d{1,5})?$");
    private static final Pattern DB_NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,64}");
    private static final Pattern MYSQL_URL = Pattern.compile("^jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?]+)");
    private static final String[][] DATA_TABLES = {{"ota_app", "个 App"}, {"ota_release", "个版本"}, {"activation_code", "个口令"},
            {"activation_record", "条激活记录"}, {"agent", "个代理"}, {"ota_admin_user", "个管理员"}};

    private final OtaProperties props;
    private final Environment env;
    private final AdminUserRepo admins;
    private final SettingRepo settings;
    private final PasswordEncoder encoder;
    private final DataSource dataSource;
    private final Restarter restarter;
    private volatile InstallConfig current;

    public InstallService(OtaProperties props, Environment env, AdminUserRepo admins, SettingRepo settings, PasswordEncoder encoder,
                          DataSource dataSource, Restarter restarter) {
        this.props = props;
        this.env = env;
        this.admins = admins;
        this.settings = settings;
        this.encoder = encoder;
        this.dataSource = dataSource;
        this.restarter = restarter;
    }

    /** 在 Web 服务开始接请求之前跑完，升级上来的旧部署不会被引导页拦住 */
    @PostConstruct
    void bootstrap() {
        InstallConfig c = InstallFiles.read(props.getDataDir());
        if (c == null) {
            if (admins.count() > 0 || settings.existsById(SettingService.ADMIN_PASSWORD)) {
                c = fromEnvironment();
                c.migrated = true;
                InstallFiles.write(props.getDataDir(), c);
                log.info("检测到已有数据，按已安装处理，配置已记到 {}", InstallFiles.file(props.getDataDir()).toAbsolutePath());
            } else if (props.getAdminPassword() != null && !props.getAdminPassword().isBlank()) {
                c = fromEnvironment();
                c.pendingAdmin = pending("admin", "admin", encoder.encode(props.getAdminPassword().trim()));
                InstallFiles.write(props.getDataDir(), c);
                log.info("已用环境变量里的管理员密码完成安装");
            }
        }
        if (c != null && c.installed && c.pendingAdmin != null) {
            applyPendingAdmin(c.pendingAdmin);
            c.pendingAdmin = null;
            InstallFiles.write(props.getDataDir(), c);
        }
        current = c;
        if (c == null || !c.installed) log.warn("还没有安装：用浏览器打开后台地址，按安装引导填写数据库、域名和管理员账号");
    }

    public boolean installed() {
        InstallConfig c = current;
        return c != null && c.installed;
    }

    public boolean restarting() {
        return restarter.restarting();
    }

    public long boot() {
        return restarter.boot();
    }

    /** 当前生效的配置，给页面展示用（密码不出现） */
    public InstallConfig snapshot() {
        InstallConfig c = current;
        return c == null ? new InstallConfig() : c;
    }

    // ===== 域名 =====

    public enum Role { ADMIN, API, ANY }

    /** 这个域名能不能访问这类地址。没开限制、没配域名、本机地址都放行 */
    public boolean allows(String host, Role role) {
        InstallConfig c = current;
        if (c == null || !c.installed || !c.restrictDomains || c.domains == null || c.domains.isEmpty() || role == Role.ANY) return true;
        if (host == null || host.isBlank()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.equals("127.0.0.1") || h.equals("[::1]") || h.equals("::1")) return true;
        for (InstallConfig.Domain d : c.domains) {
            if (!matches(d.host, h)) continue;
            if (role == Role.ADMIN ? d.admin : d.api) return true;
        }
        return false;
    }

    private static boolean matches(String pattern, String host) {
        if (pattern == null) return false;
        String p = stripPort(pattern.trim().toLowerCase(Locale.ROOT));
        if (p.startsWith("*.")) return host.endsWith(p.substring(1)) && host.length() > p.length() - 1;
        return p.equals(host);
    }

    private static String stripPort(String host) {
        int i = host.lastIndexOf(':');
        return i > 0 && host.indexOf(']') < i ? host.substring(0, i) : host;
    }

    // ===== 安装引导 =====

    public record DbForm(String type, String host, Integer port, String name, String username, String password) {}

    public record DbCheck(boolean ok, String message, boolean hasData, String summary, boolean current) {}

    /** 试连数据库，顺便看看里面有没有这个平台的数据 */
    public DbCheck testDatabase(DbForm f) {
        boolean h2 = f == null || !"mysql".equals(f.type());
        if (h2) {
            try (Connection conn = dataSource.getConnection()) {
                String summary = summarize(conn);
                return new DbCheck(true, "内置数据库可用（数据文件在 " + Path.of(props.getDataDir(), "db").toAbsolutePath().normalize() + "）",
                        summary != null, summary, true);
            } catch (Exception e) {
                return new DbCheck(false, "内置数据库打不开：" + e.getMessage(), false, null, true);
            }
        }
        String problem = checkMysqlForm(f);
        if (problem != null) return new DbCheck(false, problem, false, null, false);
        InstallConfig old = snapshot();
        String password = f.password();
        if (keepsDbPassword(f, old)) {
            try {
                password = InstallFiles.decrypt(props.getDataDir(), props.getMasterKey(), old.db.password);
            } catch (IllegalStateException e) {
                return new DbCheck(false, "保存的数据库密码解不开（主密钥和安装时用的不一致），请重新填写密码", false, null, false);
            }
        }
        InstallConfig.Database db = toDatabase(f, password);
        String url = InstallPropertySource.mysqlUrl(db) + "&connectTimeout=5000&socketTimeout=10000";
        try (Connection conn = DriverManager.getConnection(url, f.username(), password == null ? "" : password)) {
            String summary = summarize(conn);
            String version = conn.getMetaData().getDatabaseProductName() + " " + conn.getMetaData().getDatabaseProductVersion();
            return new DbCheck(true, "连接成功：" + version, summary != null, summary, sameMysql(f, old));
        } catch (Exception e) {
            return new DbCheck(false, friendly(e), false, null, false);
        }
    }

    /** 重新安装时 MySQL 密码留空：还是原来那台数据库服务器就沿用保存的密码；换了地址不沿用，免得把密码发给别的服务器 */
    private static boolean keepsDbPassword(DbForm f, InstallConfig old) {
        return blank(f.password()) && old.installed && old.db != null && old.db.mysql() && !blank(old.db.password)
                && old.db.host != null && f.host() != null && old.db.host.trim().equalsIgnoreCase(f.host().trim());
    }

    /** 填的就是现在正在用的那个 MySQL 库 */
    private static boolean sameMysql(DbForm f, InstallConfig old) {
        if (!old.installed || old.db == null || !old.db.mysql() || old.db.host == null || old.db.name == null) return false;
        int port = f.port() == null || f.port() <= 0 ? 3306 : f.port();
        int oldPort = old.db.port == null || old.db.port <= 0 ? 3306 : old.db.port;
        return old.db.host.trim().equalsIgnoreCase(f.host().trim()) && port == oldPort && old.db.name.trim().equals(f.name().trim());
    }

    private static String friendly(Exception e) {
        String m = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        if (m.contains("Access denied")) return "账号或密码不对，或者这个账号没有权限访问这个库";
        if (m.contains("Unknown database")) return "数据库不存在，请先建好（字符集选 utf8mb4）";
        if (m.contains("UnknownHost") || causedBy(e, java.net.UnknownHostException.class)) return "找不到这个数据库地址，检查一下有没有写错";
        if (m.contains("timed out") || causedBy(e, java.net.SocketTimeoutException.class)) return "连接超时：检查地址、端口，以及防火墙或云服务器安全组";
        if (m.contains("Communications link failure") || m.contains("Connection refused")) return "连不上数据库服务器，检查地址、端口和防火墙";
        return "连接失败：" + (m.length() > 160 ? m.substring(0, 160) + "…" : m);
    }

    private static boolean causedBy(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null && t.getCause() != t; t = t.getCause()) if (type.isInstance(t)) return true;
        return false;
    }

    /** 有数据返回「3 个 App、120 个口令」这样的说明，没有返回 null */
    private static String summarize(Connection conn) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        Set<String> tables = new HashSet<>();
        try (ResultSet rs = meta.getTables(conn.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (rs.next()) tables.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
        }
        List<String> parts = new ArrayList<>();
        try (Statement st = conn.createStatement()) {
            for (String[] t : DATA_TABLES) {
                if (!tables.contains(t[0])) continue;
                try (ResultSet rs = st.executeQuery("select count(*) from " + t[0])) {
                    long n = rs.next() ? rs.getLong(1) : 0;
                    if (n > 0) parts.add(n + " " + t[1]);
                }
            }
        }
        return parts.isEmpty() ? null : String.join("、", parts);
    }

    public record DomainForm(String host, boolean admin, boolean api, boolean https) {}

    public record Form(DbForm db, String dataMode, String clearConfirm, boolean restrictDomains, List<DomainForm> domains,
                       String publicUrl, String displayZone, InstallConfig.Storage storage,
                       String adminUsername, String adminName, String adminPassword, String adminPassword2) {}

    /** 校验并保存安装配置，然后重启生效。currentHost 是浏览器正在用的域名，用来防止把自己锁在外面 */
    public synchronized void submit(Form f, String currentHost) {
        if (f == null || f.db() == null) throw ApiException.badRequest("BAD_FORM", "表单不完整");
        boolean reinstall = installed();
        InstallConfig old = snapshot();
        DbCheck check = testDatabase(f.db());
        if (!check.ok()) throw ApiException.badRequest("DB_FAILED", check.message());
        boolean clear = check.hasData() && "clear".equals(f.dataMode());
        if (check.hasData() && !"keep".equals(f.dataMode()) && !"clear".equals(f.dataMode())) {
            throw ApiException.badRequest("DATA_MODE", "数据库里已经有数据，请选择保留还是清空");
        }
        if (clear && !"清空".equals(f.clearConfirm() == null ? "" : f.clearConfirm().trim())) {
            throw ApiException.badRequest("CLEAR_CONFIRM", "要清空数据，请在确认框里输入「清空」");
        }

        List<InstallConfig.Domain> domains = domains(f.domains(), f.restrictDomains(), currentHost);
        String publicUrl = f.publicUrl() == null ? "" : f.publicUrl().trim().replaceAll("/+$", "");
        if (publicUrl.isEmpty()) {
            InstallConfig.Domain api = domains.stream().filter(d -> d.api && !d.host.startsWith("*.")).findFirst().orElse(null);
            if (api != null) publicUrl = (api.https ? "https://" : "http://") + api.host;
        }
        if (!publicUrl.matches("(?i)^https?://[^\\s/]+(/[^\\s]*)?$")) {
            throw ApiException.badRequest("BAD_PUBLIC_URL", "接口对外地址要以 http:// 或 https:// 开头，比如 https://api.example.com");
        }
        publicUrl = withDomainPort(publicUrl, domains);
        String zone = f.displayZone() == null || f.displayZone().isBlank() ? "Asia/Shanghai" : f.displayZone().trim();
        try {
            ZoneId.of(zone);
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_ZONE", "时区不对：" + zone);
        }

        InstallConfig.Storage storage = f.storage() == null ? new InstallConfig.Storage() : f.storage();
        if ("s3".equals(storage.type)) {
            storage.endpoint = com.ota.platform.storage.S3Client.clean(storage.endpoint);
            storage.region = com.ota.platform.storage.S3Client.clean(storage.region);
            storage.bucket = com.ota.platform.storage.S3Client.clean(storage.bucket);
            storage.accessKey = com.ota.platform.storage.S3Client.clean(storage.accessKey);
            storage.secretKey = com.ota.platform.storage.S3Client.clean(storage.secretKey);
            if (blank(storage.endpoint) || blank(storage.bucket) || blank(storage.accessKey)) {
                throw ApiException.badRequest("BAD_STORAGE", "对象存储要填写服务地址、存储桶和 Access Key");
            }
            if (blank(storage.secretKey)) {
                if (old.storage != null && !blank(old.storage.secretKey)) storage.secretKey = old.storage.secretKey;
                else throw ApiException.badRequest("BAD_STORAGE", "请填写对象存储的 Secret Key");
            } else {
                storage.secretKey = enc(storage.secretKey.trim());
            }
        } else {
            storage = new InstallConfig.Storage();
        }

        InstallConfig.PendingAdmin admin = null;
        boolean adminGiven = !blank(f.adminUsername()) || !blank(f.adminPassword());
        boolean ownerMissing = reinstall && check.current() && !clear && admins.countByOwnerTrueAndEnabledTrue() == 0;
        boolean needAdmin = !reinstall || clear || !check.current() || adminGiven || ownerMissing;
        if (needAdmin) {
            if (ownerMissing && !adminGiven) throw ApiException.badRequest("BAD_ADMIN", "现在的数据库里没有能登录的超级管理员，请在这一步设一个");
            String u = f.adminUsername() == null ? "" : f.adminUsername().trim();
            if (!USERNAME.matcher(u).matches()) throw ApiException.badRequest("BAD_ADMIN", "管理员账号要 3–32 位字母、数字或 _ . -");
            String p = f.adminPassword() == null ? "" : f.adminPassword();
            if (p.length() < 8) throw ApiException.badRequest("BAD_ADMIN", "管理员密码至少 8 位");
            if (p.length() > 72) throw ApiException.badRequest("BAD_ADMIN", "管理员密码最多 72 位");
            if (!p.equals(f.adminPassword2())) throw ApiException.badRequest("BAD_ADMIN", "两次输入的密码不一致");
            String name = f.adminName() == null || f.adminName().isBlank() ? u : f.adminName().trim();
            admin = pending(u, name.length() > 50 ? name.substring(0, 50) : name, encoder.encode(p));
        }

        InstallConfig c = new InstallConfig();
        c.installed = true;
        c.installedAt = Instant.now().toString();
        c.version = OtaPlatformApplication.version();
        c.db = toDatabase(f.db(), null);
        if (c.db.mysql()) c.db.password = keepsDbPassword(f.db(), old) ? old.db.password : enc(f.db().password());
        c.resetSchema = clear;
        c.pendingAdmin = admin;
        c.restrictDomains = f.restrictDomains();
        c.domains = domains;
        c.publicUrl = publicUrl;
        c.displayZone = zone;
        c.storage = storage;
        InstallFiles.write(props.getDataDir(), c);
        current = c;
        log.info("安装配置已保存（{}，{}），准备重启", c.db.mysql() ? "MySQL " + c.db.host + "/" + c.db.name : "内置数据库",
                clear ? "清空数据" : check.hasData() ? "保留数据" : "新数据库");
        restarter.restartSoon();
    }

    private static String checkMysqlForm(DbForm f) {
        if (blank(f.host())) return "请填写数据库地址";
        if (f.port() != null && (f.port() < 1 || f.port() > 65535)) return "端口要在 1–65535 之间";
        if (blank(f.name()) || !DB_NAME.matcher(f.name().trim()).matches()) return "数据库名只能是字母、数字、_ 或 -";
        if (blank(f.username())) return "请填写数据库账号";
        return null;
    }

    private static InstallConfig.Database toDatabase(DbForm f, String plainPassword) {
        InstallConfig.Database d = new InstallConfig.Database();
        if (f == null || !"mysql".equals(f.type())) return d;
        d.type = "mysql";
        d.host = f.host() == null ? null : f.host().trim();
        d.port = f.port() == null || f.port() <= 0 ? 3306 : f.port();
        d.name = f.name() == null ? null : f.name().trim();
        d.username = f.username() == null ? null : f.username().trim();
        d.password = plainPassword;
        return d;
    }

    private static InstallConfig.PendingAdmin pending(String username, String name, String hash) {
        InstallConfig.PendingAdmin p = new InstallConfig.PendingAdmin();
        p.username = username;
        p.name = name;
        p.passwordHash = hash;
        return p;
    }

    /**
     * 安装后在系统配置里改访问域名和接口对外地址：校验和安装引导一样，保存后马上生效，不用重启。
     * 对外地址留空时按第一个接口域名生成（带上端口），文档、接入配置里显示的都是它
     */
    public synchronized void updateDomains(boolean restrict, List<DomainForm> forms, String publicUrl, String currentHost) {
        InstallConfig c = current;
        if (c == null || !c.installed) throw ApiException.badRequest("NOT_INSTALLED", "还没有安装");
        List<InstallConfig.Domain> domains = domains(forms, restrict, currentHost);
        String url = publicUrl(publicUrl, domains);
        c.domains = domains;
        c.restrictDomains = restrict;
        if (!url.isEmpty()) c.publicUrl = url;
        InstallFiles.write(props.getDataDir(), c);
        current = InstallFiles.read(props.getDataDir());
        if (!url.isEmpty() && !fromStartup("ota.public-url")) props.setPublicUrl(url);
    }

    private static String publicUrl(String typed, List<InstallConfig.Domain> domains) {
        String url = typed == null ? "" : typed.trim().replaceAll("/+$", "");
        if (url.isEmpty()) {
            InstallConfig.Domain api = domains.stream().filter(d -> d.api && !d.host.startsWith("*.")).findFirst().orElse(null);
            if (api == null) return "";
            url = (api.https ? "https://" : "http://") + api.host;
        }
        if (!url.matches("(?i)^https?://[^\\s/]+(/[^\\s]*)?$")) {
            throw ApiException.badRequest("BAD_PUBLIC_URL", "接口对外地址要以 http:// 或 https:// 开头，比如 https://api.example.com:8813");
        }
        return withDomainPort(url, domains);
    }

    private static final Pattern URL_PARTS = Pattern.compile("(?i)^(https?://)([^/:\\s\\[\\]]+)(:\\d+)?(/.*)?$");

    /**
     * 对外地址和某个接口域名是同一台主机、那个域名写了端口、地址里却没写端口时，补上端口。
     * 反向代理（宝塔的 Nginx 默认 Host $host）会把端口去掉，页面上也容易漏写；少了端口，App 下载文件的地址就不对
     */
    static String withDomainPort(String url, List<InstallConfig.Domain> domains) {
        if (url == null || domains == null) return url;
        java.util.regex.Matcher m = URL_PARTS.matcher(url);
        if (!m.matches() || m.group(3) != null) return url;
        String host = m.group(2).toLowerCase(Locale.ROOT);
        for (InstallConfig.Domain d : domains) {
            if (!d.api || d.host == null) continue;
            String dh = d.host.trim().toLowerCase(Locale.ROOT);
            int i = dh.lastIndexOf(':');
            if (i > 0 && dh.indexOf(']') < i && dh.substring(0, i).equals(host)) {
                return m.group(1) + m.group(2) + dh.substring(i) + (m.group(4) == null ? "" : m.group(4));
            }
        }
        return url;
    }

    /** 现在的对外地址漏了端口时，建议改成的地址；不用改返回 null */
    public String publicUrlFix() {
        InstallConfig c = current;
        String now = props.baseUrl();
        if (c == null || now.isEmpty()) return null;
        String fixed = withDomainPort(now, c.domains);
        return fixed.equals(now) ? null : fixed;
    }

    /** 只改对外地址（系统配置里「补上端口」） */
    public synchronized void updatePublicUrl(String url) {
        InstallConfig c = current;
        if (c == null || !c.installed) throw ApiException.badRequest("NOT_INSTALLED", "还没有安装");
        String u = url == null ? "" : url.trim().replaceAll("/+$", "");
        if (!u.matches("(?i)^https?://[^\\s/]+(/[^\\s]*)?$")) {
            throw ApiException.badRequest("BAD_PUBLIC_URL", "接口对外地址要以 http:// 或 https:// 开头，比如 https://api.example.com:8813");
        }
        c.publicUrl = u;
        InstallFiles.write(props.getDataDir(), c);
        current = InstallFiles.read(props.getDataDir());
        if (!fromStartup("ota.public-url")) props.setPublicUrl(u);
    }

    private static final Set<String> STARTUP_SOURCES = Set.of(CommandLinePropertySource.COMMAND_LINE_PROPERTY_SOURCE_NAME,
            StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);

    /** 这个配置是不是启动参数或环境变量给的：它们比 install.json 优先，在页面上改了，重启后又会变回去。application.yml 里的默认值不算 */
    public boolean fromStartup(String key) {
        if (!(env instanceof ConfigurableEnvironment ce)) return false;
        for (PropertySource<?> ps : ce.getPropertySources()) {
            if (STARTUP_SOURCES.contains(ps.getName()) && ps.containsProperty(key)) return true;
        }
        return false;
    }

    /** 系统配置里切换文件存储后记下来，重启后照样用新的存储。密钥加密保存 */
    public synchronized void saveStorage(InstallConfig.Storage storage) {
        InstallConfig c = current;
        if (c == null || !c.installed) throw ApiException.badRequest("NOT_INSTALLED", "还没有安装");
        InstallConfig.Storage s = storage == null ? new InstallConfig.Storage() : storage;
        if ("s3".equals(s.type) && !blank(s.secretKey)) s.secretKey = enc(s.secretKey);
        c.storage = s;
        InstallFiles.write(props.getDataDir(), c);
        current = InstallFiles.read(props.getDataDir());
    }

    /** 整理、校验域名列表。currentHost 是浏览器正在用的域名：开了限制却不在后台域名里，就会把自己锁在外面 */
    private static List<InstallConfig.Domain> domains(List<DomainForm> forms, boolean restrict, String currentHost) {
        List<InstallConfig.Domain> domains = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (forms != null) {
            for (DomainForm d : forms) {
                String host = d.host() == null ? "" : d.host().trim().toLowerCase(Locale.ROOT).replaceFirst("^https?://", "").replaceAll("/.*$", "");
                if (host.isEmpty()) continue;
                if (!HOST.matcher(host).matches()) throw ApiException.badRequest("BAD_DOMAIN", "域名格式不对：" + host);
                if (!d.admin() && !d.api()) throw ApiException.badRequest("BAD_DOMAIN", host + " 至少要用于后台或接口其中一项");
                if (!seen.add(host)) throw ApiException.badRequest("BAD_DOMAIN", host + " 填了两次");
                InstallConfig.Domain x = new InstallConfig.Domain();
                x.host = host;
                x.admin = d.admin();
                x.api = d.api();
                x.https = d.https();
                domains.add(x);
            }
        }
        if (domains.size() > 30) throw ApiException.badRequest("BAD_DOMAIN", "最多 30 个域名");
        if (restrict) {
            if (domains.stream().noneMatch(d -> d.admin)) throw ApiException.badRequest("BAD_DOMAIN", "开了域名限制，至少要有一个后台域名");
            if (domains.stream().noneMatch(d -> d.api)) throw ApiException.badRequest("BAD_DOMAIN", "开了域名限制，至少要有一个接口域名，不然 App 访问不了");
            String h = currentHost == null ? "" : currentHost.toLowerCase(Locale.ROOT);
            boolean local = h.equals("localhost") || h.equals("127.0.0.1");
            if (!local && domains.stream().noneMatch(d -> d.admin && matches(d.host, h))) {
                throw ApiException.badRequest("LOCKOUT", "你现在用的 " + currentHost + " 不在后台域名里，保存后就打不开后台了。请把它加进去，或者先不开域名限制");
            }
        }
        return domains;
    }

    /** 安装引导里填的管理员：账号已存在就重置成超级管理员并改密码，不存在就新建 */
    private void applyPendingAdmin(InstallConfig.PendingAdmin p) {
        AdminUser u = admins.findByUsernameIgnoreCase(p.username).orElseGet(AdminUser::new);
        boolean created = u.getId() == null;
        u.setUsername(p.username);
        u.setName(p.name == null || p.name.isBlank() ? p.username : p.name);
        u.setPasswordHash(p.passwordHash);
        u.setOwner(true);
        u.setEnabled(true);
        u.setCredentialsAt(Instant.now());
        admins.save(u);
        log.info("{}超级管理员 {}", created ? "已创建" : "已重置", p.username);
    }

    /** 当前生效的配置（启动参数、环境变量、application.yml 合起来的结果）转成 install.json，方便以后去掉启动参数 */
    private InstallConfig fromEnvironment() {
        InstallConfig c = new InstallConfig();
        c.installed = true;
        c.installedAt = Instant.now().toString();
        c.version = OtaPlatformApplication.version();
        String url = env.getProperty("spring.datasource.url", "");
        Matcher m = MYSQL_URL.matcher(url);
        if (m.find()) {
            c.db.type = "mysql";
            c.db.host = m.group(1);
            c.db.port = m.group(2) == null ? 3306 : Integer.parseInt(m.group(2));
            c.db.name = m.group(3);
            c.db.username = env.getProperty("spring.datasource.username", "");
            c.db.password = enc(env.getProperty("spring.datasource.password", ""));
        }
        c.publicUrl = props.getPublicUrl();
        c.displayZone = props.getDisplayZone();
        OtaProperties.S3 s3 = props.getStorage().getS3();
        if ("s3".equals(props.getStorage().getType())) {
            c.storage.type = "s3";
            c.storage.endpoint = s3.getEndpoint();
            c.storage.region = s3.getRegion();
            c.storage.bucket = s3.getBucket();
            c.storage.accessKey = s3.getAccessKey();
            c.storage.secretKey = enc(s3.getSecretKey());
            c.storage.pathStyle = s3.isPathStyle();
            c.storage.prefix = s3.getPrefix();
            c.storage.publicUrl = s3.getPublicUrl();
        }
        return c;
    }

    /** 系统配置里「安装信息」页用的：实际生效的数据库地址（不含密码） */
    public String effectiveDatabase() {
        String url = env.getProperty("spring.datasource.url", "");
        if (url.startsWith("jdbc:h2:")) return "内置数据库 · " + Path.of(props.getDataDir(), "db").toAbsolutePath().normalize();
        return url.replaceAll("\\?.*$", "");
    }

    public Path dataDir() {
        return Path.of(props.getDataDir()).toAbsolutePath().normalize();
    }

    public long freeDiskBytes() {
        try {
            return Files.getFileStore(dataDir()).getUsableSpace();
        } catch (Exception e) {
            return -1;
        }
    }

    private String enc(String plain) {
        return InstallFiles.encrypt(props.getDataDir(), props.getMasterKey(), plain);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
