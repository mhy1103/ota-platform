package com.ota.platform.demo;

import com.ota.platform.OtaPlatformApplication;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.settings.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.logging.LoggingApplicationListener;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 演示环境：在同一个进程里另起一个完整的应用（自己的数据目录、数据库、文件、缓存），只监听本机随机端口，
 * 用演示账号登录的浏览器请求都转给它（DemoProxyFilter）。演示数据来自 jar 里的 demo/seed.zip，
 * 准备时把里面所有时间平移到现在，看起来就是最近的数据。没人用 30 分钟自动停掉，数据超过一天下次启动时恢复默认。
 */
@Component
@ConditionalOnProperty(name = "ota.demo.mode", havingValue = "false", matchIfMissing = true)
public class DemoSandbox implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DemoSandbox.class);
    public static final String ENABLED = "demo.enabled", USER = "demo.username", PASS = "demo.password";
    public static final String DEFAULT_USER = "demo", DEFAULT_PASS = "demo2026";
    static final Duration IDLE = Duration.ofMinutes(30), MAX_AGE = Duration.ofHours(24);

    private final OtaProperties props;
    private final SettingService settings;
    private final PasswordEncoder encoder;
    private final HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final Object lock = new Object();
    private volatile ConfigurableApplicationContext child;
    private volatile int port;
    private volatile long lastUsed;
    private volatile String secret;
    private volatile boolean resetAsked;

    public DemoSandbox(OtaProperties props, SettingService settings, PasswordEncoder encoder) {
        this.props = props;
        this.settings = settings;
        this.encoder = encoder;
    }

    // ==================== 账号 ====================

    public boolean enabled() {
        return !"false".equals(settings.get(ENABLED, "true"));
    }

    public String username() {
        return settings.get(USER, DEFAULT_USER);
    }

    private String hash() {
        String h = settings.get(PASS, null);
        if (h == null) {
            h = encoder.encode(DEFAULT_PASS);
            settings.set(PASS, h);
        }
        return h;
    }

    public boolean matches(String user, String password) {
        return enabled() && user != null && password != null && user.trim().equalsIgnoreCase(username()) && encoder.matches(password, hash());
    }

    public void configure(boolean enabled, String username, String password) {
        settings.set(ENABLED, String.valueOf(enabled));
        if (username != null && !username.isBlank()) settings.set(USER, username.trim().toLowerCase(Locale.ROOT));
        if (password != null && !password.isEmpty()) settings.set(PASS, encoder.encode(password));
        if (!enabled) stop();
    }

    // ==================== 运行 ====================

    public boolean running() {
        ConfigurableApplicationContext c = child;
        return c != null && c.isActive();
    }

    public void touch() {
        lastUsed = System.currentTimeMillis();
    }

    public Path dir() {
        return Path.of(props.getDataDir()).toAbsolutePath().normalize().resolve("demo");
    }

    /** 演示数据是什么时候准备的（还没准备过是 null） */
    public Instant createdAt() {
        try {
            return Instant.parse(Files.readString(dir().resolve(".created")).trim());
        } catch (Exception e) {
            return null;
        }
    }

    /** 确保演示环境在跑，返回端口；第一次要准备数据和启动，大概几秒 */
    public int ensureRunning() throws Exception {
        synchronized (lock) {
            touch();
            if (running()) return port;
            prepare();
            start();
            return port;
        }
    }

    /** 恢复默认演示数据：停掉，下次有人登录时重新准备 */
    public void reset() {
        synchronized (lock) {
            stop();
            resetAsked = true;
        }
    }

    private void prepare() throws IOException, SQLException {
        Path d = dir();
        Instant created = createdAt();
        boolean stale = created == null || Duration.between(created, Instant.now()).compareTo(MAX_AGE) > 0;
        if (!resetAsked && !stale && Files.isDirectory(d.resolve("db"))) return;
        deleteTree(d);
        Files.createDirectories(d);
        String ref = null;
        ClassPathResource seed = new ClassPathResource("demo/seed.zip");
        try (InputStream raw = seed.getInputStream(); ZipInputStream zip = new ZipInputStream(raw)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                if (e.isDirectory()) continue;
                Path target = d.resolve(e.getName()).normalize();
                if (!target.startsWith(d)) continue;
                Files.createDirectories(target.getParent());
                Files.copy(zip, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        Path refFile = d.resolve("seed.properties");
        if (Files.isRegularFile(refFile)) {
            Properties p = new Properties();
            try (InputStream in = Files.newInputStream(refFile)) {
                p.load(in);
            }
            ref = p.getProperty("ref");
        }
        if (ref != null) shiftTimes(d, OffsetDateTime.parse(ref));
        Files.writeString(d.resolve(".created"), Instant.now().toString());
        resetAsked = false;
        log.info("演示数据已准备好：{}", d);
    }

    /** 把演示数据库里所有时间往后挪，让最近的数据落在今天；登录会话清空 */
    private void shiftTimes(Path d, OffsetDateTime ref) throws SQLException {
        ZoneId zone;
        try {
            zone = ZoneId.of(props.getDisplayZone());
        } catch (Exception e) {
            zone = ZoneId.systemDefault();
        }
        long seconds = Duration.between(ref.toInstant(), Instant.now()).getSeconds();
        long days = ChronoUnit.DAYS.between(ref.atZoneSameInstant(zone).toLocalDate(), LocalDate.now(zone));
        try (Connection c = DriverManager.getConnection("jdbc:h2:file:" + d.resolve("db").resolve("ota") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "")) {
            List<String[]> cols = new ArrayList<>();
            try (ResultSet rs = c.getMetaData().getColumns(null, "public", "%", "%")) {
                while (rs.next()) {
                    String type = rs.getString("TYPE_NAME").toUpperCase(Locale.ROOT);
                    if (type.contains("TIMESTAMP") || type.equals("DATE")) {
                        cols.add(new String[]{rs.getString("TABLE_NAME"), rs.getString("COLUMN_NAME"), type.equals("DATE") ? "DAY" : "SECOND"});
                    }
                }
            }
            try (Statement st = c.createStatement()) {
                for (String[] col : cols) {
                    if (col[0].startsWith("spring_session")) continue;
                    long n = "DAY".equals(col[2]) ? days : seconds;
                    if (n == 0) continue;
                    st.executeUpdate("update \"" + col[0] + "\" set \"" + col[1] + "\" = dateadd(" + col[2] + ", " + n + ", \"" + col[1]
                            + "\") where \"" + col[1] + "\" is not null");
                }
                try {
                    st.executeUpdate("delete from spring_session_attributes");
                    st.executeUpdate("delete from spring_session");
                } catch (SQLException ignored) {
                    // 没有会话表就算了
                }
            }
        }
    }

    private void start() {
        secret = randomSecret();
        Path d = dir();
        SpringApplication app = new SpringApplication(OtaPlatformApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        app.setRegisterShutdownHook(false);
        // 日志由正式环境配置好了，演示环境不要再初始化一遍（会把正式环境的日志设置重置掉）
        app.setListeners(app.getListeners().stream().filter(l -> !(l instanceof LoggingApplicationListener)).toList());
        String db = "jdbc:h2:file:" + d.resolve("db").resolve("ota") + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE";
        child = app.run(
                "--server.port=0", "--server.address=127.0.0.1", "--server.shutdown=immediate",
                "--server.forward-headers-strategy=framework",
                "--ota.data-dir=" + d, "--ota.demo.mode=true", "--ota.demo.admin=" + username(), "--ota.demo.secret=" + secret,
                "--ota.public-url=" + props.baseUrl(), "--ota.display-zone=" + props.getDisplayZone(),
                "--ota.master-key=", "--ota.storage.type=local",
                "--spring.datasource.url=" + db, "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--spring.servlet.multipart.max-file-size=60MB", "--spring.servlet.multipart.max-request-size=60MB",
                "--spring.main.banner-mode=off");
        port = ((WebServerApplicationContext) child).getWebServer().getPort();
        log.info("演示环境已启动（本机端口 {}）", port);
    }

    public void stop() {
        synchronized (lock) {
            ConfigurableApplicationContext c = child;
            child = null;
            if (c == null) return;
            try {
                c.close();
                log.info("演示环境已停止");
            } catch (Exception e) {
                log.warn("停止演示环境出错：{}", e.toString());
            }
        }
    }

    /** 没人用了就停掉，省内存 */
    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void idleCheck() {
        if (running() && System.currentTimeMillis() - lastUsed > IDLE.toMillis()) stop();
    }

    @Override
    public void destroy() {
        stop();
    }

    public int port() {
        return port;
    }

    /** 代用户登录演示环境：返回演示环境的会话号 */
    public String login(String host, String proto) throws Exception {
        int p = ensureRunning();
        String body = "username=" + java.net.URLEncoder.encode(username(), StandardCharsets.UTF_8)
                + "&password=" + java.net.URLEncoder.encode(secret, StandardCharsets.UTF_8);
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + p + "/login"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "Mozilla/5.0 (OTA demo login)")
                .POST(HttpRequest.BodyPublishers.ofString(body));
        if (host != null) b.header("X-Forwarded-Host", host);
        if (proto != null) b.header("X-Forwarded-Proto", proto);
        HttpResponse<Void> res = http.send(b.build(), HttpResponse.BodyHandlers.discarding());
        for (String c : res.headers().allValues("set-cookie")) {
            if (c.startsWith("OTA_SESSION=")) {
                String v = c.substring("OTA_SESSION=".length());
                int semi = v.indexOf(';');
                return semi < 0 ? v : v.substring(0, semi);
            }
        }
        throw new IllegalStateException("演示环境登录失败（HTTP " + res.statusCode() + "）");
    }

    HttpClient http() {
        return http;
    }

    private static String randomSecret() {
        byte[] b = new byte[24];
        new SecureRandom().nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> all = Files.walk(p)) {
            for (Path f : (Iterable<Path>) all.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(f);
        }
    }
}
