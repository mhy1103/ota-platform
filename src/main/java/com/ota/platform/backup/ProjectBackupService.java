package com.ota.platform.backup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ota.platform.OtaPlatformApplication;
import com.ota.platform.app.AppService;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.settings.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.Statement;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.LongConsumer;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 项目备份：整个后台的数据（数据库、所有 App 的版本文件和图标、签名私钥、主密钥、安装配置）打成一个包。
 * 下载时用备份密码加密（ProjectCrypto）；自动备份存在服务器的 data/backups 下（和数据目录同样敏感，不另外加密）。
 * 恢复：先把备份解好放到 data/restore 下，重启时在打开数据库之前替换（RestoreOnStart），原来的数据留在 data/restore/before-时间。
 */
@Service
public class ProjectBackupService {

    private static final Logger log = LoggerFactory.getLogger(ProjectBackupService.class);
    public static final String FORMAT = "ota-project-backup";
    public static final int VERSION = 1;
    public static final String AUTO = "backup.auto", KEEP = "backup.keep", HOUR = "backup.hour", LAST = "backup.last";
    /** 数据目录里属于数据的部分；其余（tmp、backups、restore、演示环境）不备份 */
    static final List<String> DATA_ITEMS = List.of("blobs", "master.key", "install.json");
    private static final Pattern STORED = Pattern.compile("[A-Za-z0-9._-]{1,120}\\.(zip|otaproj)");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final OtaProperties props;
    private final DataSource dataSource;
    private final SettingService settings;
    private final AppService apps;
    private final ObjectMapper json = new ObjectMapper();

    public ProjectBackupService(OtaProperties props, DataSource dataSource, SettingService settings, AppService apps) {
        this.props = props;
        this.dataSource = dataSource;
        this.settings = settings;
        this.apps = apps;
    }

    public Path dataDir() {
        return Path.of(props.getDataDir()).toAbsolutePath().normalize();
    }

    private Path storedDir() throws IOException {
        return Files.createDirectories(dataDir().resolve("backups"));
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(props.getDisplayZone());
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }

    // ==================== 打包 ====================

    /** 写一个完整备份（zip）到 out，不关 out */
    public void write(OutputStream out) throws IOException {
        ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(out, 1 << 16));
        zip.setLevel(5);
        Path dir = dataDir();
        boolean h2 = isH2();
        ObjectNode m = json.createObjectNode();
        m.put("format", FORMAT);
        m.put("version", VERSION);
        m.put("createdAt", OffsetDateTime.now(zone()).toString());
        m.put("appVersion", OtaPlatformApplication.version());
        m.put("database", h2 ? "h2" : "external");
        m.put("storage", "s3".equals(props.getStorage().getType()) ? "s3" : "local");
        m.put("apps", apps.all().size());
        zip.putNextEntry(new ZipEntry("manifest.json"));
        zip.write(json.writerWithDefaultPrettyPrinter().writeValueAsBytes(m));
        zip.closeEntry();
        if (h2) {
            Path tmp = Files.createDirectories(dir.resolve("tmp")).resolve("db-" + UUID.randomUUID() + ".zip");
            try {
                try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
                    st.execute("BACKUP TO '" + tmp.toString().replace("'", "''") + "'");
                }
                zip.putNextEntry(new ZipEntry("db/ota.zip"));
                Files.copy(tmp, zip);
                zip.closeEntry();
            } catch (java.sql.SQLException e) {
                throw new IOException("备份数据库失败：" + e.getMessage(), e);
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        boolean s3 = props.getStorage().isS3();
        for (String item : DATA_ITEMS) {
            // 文件在 S3 里时，本机 blobs 只是切换前留下的旧副本
            if (s3 && item.equals("blobs")) continue;
            Path p = dir.resolve(item);
            if (!Files.exists(p)) continue;
            try (Stream<Path> files = Files.walk(p)) {
                for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile).sorted()::iterator) {
                    // 先打开再写条目：遍历期间被清理掉的文件直接跳过，不会留下空条目
                    InputStream in;
                    try {
                        in = Files.newInputStream(f);
                    } catch (NoSuchFileException gone) {
                        continue;
                    }
                    try (in) {
                        zip.putNextEntry(new ZipEntry("data/" + dir.relativize(f).toString().replace('\\', '/')));
                        in.transferTo(zip);
                        zip.closeEntry();
                    }
                }
            }
        }
        zip.finish();
        zip.flush();
    }

    // ==================== 下载用的临时文件 ====================

    private Path downloadDir() throws IOException {
        return Files.createDirectories(dataDir().resolve("tmp").resolve("downloads"));
    }

    /** 打包一份完整备份，用备份密码加密，存到临时目录；progress 收到已写出的字节数 */
    public Path prepare(String password, LongConsumer progress) throws IOException {
        return toTemp(progress, out -> write(out), password);
    }

    /** 服务器上存的 .zip 用备份密码加密成临时文件 */
    public Path encryptStored(String name, String password, LongConsumer progress) throws IOException {
        Path f = storedFile(name);
        return toTemp(progress, out -> {
            try (InputStream in = Files.newInputStream(f)) {
                in.transferTo(out);
            }
        }, password);
    }

    private interface Body {
        void write(OutputStream out) throws IOException;
    }

    private Path toTemp(LongConsumer progress, Body body, String password) throws IOException {
        cleanDownloads();
        Path dir = downloadDir(), part = dir.resolve("backup-" + UUID.randomUUID() + ".part");
        try {
            try (OutputStream file = new Counting(Files.newOutputStream(part), progress);
                 OutputStream enc = ProjectCrypto.encrypt(new BufferedOutputStream(file, 1 << 16), password)) {
                body.write(enc);
            }
            return Files.move(part, dir.resolve(part.getFileName().toString().replace(".part", ".otaproj")));
        } catch (IOException | RuntimeException e) {
            Files.deleteIfExists(part);
            throw e;
        }
    }

    /** 没被下载走的临时文件，放一小时后删掉 */
    @Scheduled(cron = "0 17 * * * *")
    public void cleanDownloads() {
        Instant old = Instant.now().minus(Duration.ofHours(1));
        try (Stream<Path> files = Files.list(downloadDir())) {
            for (Path f : (Iterable<Path>) files::iterator) {
                try {
                    if (Files.getLastModifiedTime(f).toInstant().isBefore(old)) Files.deleteIfExists(f);
                } catch (IOException ignored) {
                    // 删不掉下次再删
                }
            }
        } catch (IOException e) {
            log.debug("清理下载临时文件失败：{}", e.toString());
        }
    }

    private static final class Counting extends FilterOutputStream {
        private final LongConsumer progress;
        private long n;

        Counting(OutputStream out, LongConsumer progress) {
            super(out);
            this.progress = progress;
        }

        @Override
        public void write(int b) throws IOException {
            out.write(b);
            progress.accept(++n);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
            progress.accept(n += len);
        }
    }

    public boolean isH2() {
        try (Connection c = dataSource.getConnection()) {
            return c.getMetaData().getURL().startsWith("jdbc:h2:");
        } catch (Exception e) {
            return false;
        }
    }

    // ==================== 自动备份 ====================

    public record Stored(String name, long size, LocalDateTime at, boolean encrypted) {}

    public List<Stored> stored() {
        List<Stored> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(storedDir())) {
            files.filter(f -> Files.isRegularFile(f) && STORED.matcher(f.getFileName().toString()).matches()).forEach(f -> {
                try {
                    BasicFileAttributes a = Files.readAttributes(f, BasicFileAttributes.class);
                    out.add(new Stored(f.getFileName().toString(), a.size(),
                            LocalDateTime.ofInstant(a.lastModifiedTime().toInstant(), ZoneId.systemDefault()), f.toString().endsWith(".otaproj")));
                } catch (IOException ignored) {
                    // 读不到就不列
                }
            });
        } catch (IOException e) {
            log.debug("列备份失败：{}", e.toString());
        }
        out.sort(Comparator.comparing(Stored::at).reversed());
        return out;
    }

    public Path storedFile(String name) throws IOException {
        if (name == null || !STORED.matcher(name).matches()) throw new FileNotFoundException("备份文件不存在");
        Path f = storedDir().resolve(name).normalize();
        if (!f.getParent().equals(storedDir()) || !Files.isRegularFile(f)) throw new FileNotFoundException("备份文件不存在");
        return f;
    }

    /** 在服务器上存一份（自动备份、「现在备份一次」），然后按保留份数清掉旧的自动备份 */
    public Stored backupNow() throws IOException {
        return backupNow(n -> { });
    }

    /** progress 收到已写出的字节数 */
    public synchronized Stored backupNow(LongConsumer progress) throws IOException {
        Path dir = storedDir();
        String name = "ota-" + FILE_TIME.format(LocalDateTime.now(zone())) + ".zip";
        Path tmp = dir.resolve(name + ".part");
        try (OutputStream os = new Counting(Files.newOutputStream(tmp), progress)) {
            write(os);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
        Path f = Files.move(tmp, dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        settings.set(LAST, LocalDateTime.now().toString());
        prune();
        return new Stored(name, Files.size(f), LocalDateTime.now(), false);
    }

    private void prune() {
        int keep = Math.max(1, settings.getInt(KEEP, 7));
        List<Stored> autos = stored().stream().filter(s -> s.name().startsWith("ota-") && !s.encrypted()).toList();
        for (int i = keep; i < autos.size(); i++) {
            try {
                Files.deleteIfExists(storedDir().resolve(autos.get(i).name()));
            } catch (IOException e) {
                log.warn("清理旧备份失败：{}", e.toString());
            }
        }
    }

    public void delete(String name) throws IOException {
        Files.delete(storedFile(name));
    }

    /** 每小时看一次：开了自动备份、到了设定的钟点、今天还没备份过，就备份一次 */
    @Scheduled(cron = "0 7 * * * *")
    public void auto() {
        if (!"true".equals(settings.get(AUTO, "false"))) return;
        LocalDateTime now = LocalDateTime.now(zone());
        if (now.getHour() != settings.getInt(HOUR, 3)) return;
        LocalDate today = now.toLocalDate();
        boolean done = stored().stream().anyMatch(s -> s.name().startsWith("ota-" + today.toString().replace("-", "")));
        if (done) return;
        try {
            Stored s = backupNow();
            log.info("自动备份完成：{}（{} 字节）", s.name(), s.size());
        } catch (Exception e) {
            log.error("自动备份失败", e);
        }
    }

    public LocalDateTime lastBackup() {
        try {
            return LocalDateTime.parse(settings.get(LAST, ""));
        } catch (Exception e) {
            return null;
        }
    }

    /** 备份里会有的内容压缩前多大：数据库只算数据文件（不算 trace 日志、临时文件），文件只算本机存储的 */
    public record Sizes(long db, long files) {
        public long total() { return db + files; }
    }

    public Sizes sizes() {
        Path dir = dataDir();
        long db = isH2() ? sum(dir.resolve("db"), f -> {
            String n = f.getFileName().toString();
            return n.endsWith(".mv.db") || n.endsWith(".h2.db");
        }) : 0;
        long files = props.getStorage().isS3() ? 0 : sum(dir.resolve("blobs"), f -> true);
        files += sum(dir.resolve("master.key"), f -> true) + sum(dir.resolve("install.json"), f -> true);
        return new Sizes(db, files);
    }

    public long dataSize() {
        return sizes().total();
    }

    private static long sum(Path p, java.util.function.Predicate<Path> keep) {
        if (!Files.exists(p)) return 0;
        try (Stream<Path> files = Files.walk(p)) {
            return files.filter(Files::isRegularFile).filter(keep).mapToLong(f -> f.toFile().length()).sum();
        } catch (IOException e) {
            return 0;
        }
    }

    // ==================== 恢复 ====================

    public record Pending(String createdAt, String appVersion, int apps, String stagedBy, String stagedAt) {}

    private Path restoreDir() throws IOException {
        return Files.createDirectories(dataDir().resolve("restore"));
    }

    /** 上传的加密备份：解密、检查，放好等重启 */
    public Pending stageEncrypted(Path file, String password, String by) throws IOException {
        Path out = restoreDir().resolve("pending.zip.part");
        try {
            ProjectCrypto.decrypt(file, password, out);
            return stage(out, by);
        } finally {
            Files.deleteIfExists(out);
        }
    }

    /** 服务器上存的备份：.zip 直接用，.otaproj 要密码 */
    public Pending stageStored(String name, String password, String by) throws IOException {
        Path f = storedFile(name);
        if (f.toString().endsWith(".otaproj") || ProjectCrypto.looksEncrypted(f)) return stageEncrypted(f, password, by);
        Path copy = restoreDir().resolve("pending.zip.part");
        try {
            Files.copy(f, copy, StandardCopyOption.REPLACE_EXISTING);
            return stage(copy, by);
        } finally {
            Files.deleteIfExists(copy);
        }
    }

    private Pending stage(Path zip, String by) throws IOException {
        JsonNode m;
        try (ZipFile zf = new ZipFile(zip.toFile())) {
            ZipEntry e = zf.getEntry("manifest.json");
            if (e == null) throw new IOException("不是项目备份文件（缺少 manifest.json）");
            try (InputStream in = zf.getInputStream(e)) {
                m = json.readTree(in);
            }
            if (!FORMAT.equals(m.path("format").asText()) || m.path("version").asInt() > VERSION) {
                throw new IOException("不是这个后台的项目备份，或者是更新版本做的备份");
            }
            for (Enumeration<? extends ZipEntry> it = zf.entries(); it.hasMoreElements(); ) {
                String n = it.nextElement().getName();
                if (n.contains("..") || n.startsWith("/") || n.contains("\\")) throw new IOException("备份文件里有不安全的路径：" + n);
            }
        } catch (java.util.zip.ZipException e) {
            throw new IOException("备份文件损坏了，解不开");
        }
        Path dir = restoreDir();
        Files.move(zip, dir.resolve("pending.zip"), StandardCopyOption.REPLACE_EXISTING);
        ObjectNode marker = json.createObjectNode();
        marker.put("createdAt", m.path("createdAt").asText(""));
        marker.put("appVersion", m.path("appVersion").asText(""));
        marker.put("apps", m.path("apps").asInt());
        marker.put("stagedBy", by);
        marker.put("stagedAt", LocalDateTime.now(zone()).toString());
        Files.write(dir.resolve("PENDING.json"), json.writeValueAsBytes(marker));
        return pending();
    }

    public Pending pending() {
        try {
            Path marker = dataDir().resolve("restore").resolve("PENDING.json");
            if (!Files.isRegularFile(marker)) return null;
            JsonNode n = json.readTree(marker.toFile());
            return new Pending(n.path("createdAt").asText(""), n.path("appVersion").asText(""), n.path("apps").asInt(),
                    n.path("stagedBy").asText(""), n.path("stagedAt").asText(""));
        } catch (IOException e) {
            return null;
        }
    }

    public void cancelPending() throws IOException {
        Path dir = dataDir().resolve("restore");
        Files.deleteIfExists(dir.resolve("PENDING.json"));
        Files.deleteIfExists(dir.resolve("pending.zip"));
    }

    /** 上一次恢复的结果（重启后在页面上提示一次） */
    public JsonNode lastRestore() {
        try {
            Path f = dataDir().resolve("restore").resolve("last.json");
            return Files.isRegularFile(f) ? json.readTree(f.toFile()) : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 启动时（打开数据库之前）调用：有准备好的恢复就替换数据。原来的数据挪到 restore/before-时间，出错就挪回去。
     * 只留最近两份 before-*，免得磁盘越占越多。
     */
    public static void applyPending(Path dataDir) {
        Path restore = dataDir.resolve("restore"), marker = restore.resolve("PENDING.json"), zip = restore.resolve("pending.zip");
        if (!Files.isRegularFile(marker) || !Files.isRegularFile(zip)) return;
        String stamp = FILE_TIME.format(LocalDateTime.now());
        Path before = restore.resolve("before-" + stamp);
        List<String> moved = new ArrayList<>();
        ObjectMapper om = new ObjectMapper();
        try {
            Files.createDirectories(before);
            for (String item : List.of("db", "blobs", "master.key", "install.json")) {
                Path p = dataDir.resolve(item);
                if (Files.exists(p)) {
                    Files.move(p, before.resolve(item));
                    moved.add(item);
                }
            }
            try (ZipFile zf = new ZipFile(zip.toFile())) {
                for (Enumeration<? extends ZipEntry> it = zf.entries(); it.hasMoreElements(); ) {
                    ZipEntry e = it.nextElement();
                    if (e.isDirectory()) continue;
                    String n = e.getName();
                    if (n.startsWith("data/")) {
                        String rel = n.substring(5);
                        String top = rel.contains("/") ? rel.substring(0, rel.indexOf('/')) : rel;
                        if (!DATA_ITEMS.contains(top)) continue;
                        Path target = dataDir.resolve(rel).normalize();
                        if (!target.startsWith(dataDir)) continue;
                        Files.createDirectories(target.getParent());
                        try (InputStream in = zf.getInputStream(e)) {
                            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } else if (n.equals("db/ota.zip")) {
                        Path dbDir = Files.createDirectories(dataDir.resolve("db"));
                        try (ZipInputStream inner = new ZipInputStream(zf.getInputStream(e))) {
                            for (ZipEntry d; (d = inner.getNextEntry()) != null; ) {
                                if (d.isDirectory()) continue;
                                Path target = dbDir.resolve(Path.of(d.getName()).getFileName().toString()).normalize();
                                if (!target.startsWith(dbDir)) continue;
                                Files.copy(inner, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }
            }
            JsonNode m = om.readTree(marker.toFile());
            ObjectNode last = om.createObjectNode();
            last.put("ok", true);
            last.put("restoredAt", LocalDateTime.now().toString());
            last.put("backupCreatedAt", m.path("createdAt").asText(""));
            last.put("before", dataDir.relativize(before).toString());
            Files.write(restore.resolve("last.json"), om.writeValueAsBytes(last));
            Files.deleteIfExists(marker);
            Files.deleteIfExists(zip);
            System.out.println("[OTA] 已从项目备份恢复数据，原来的数据在 " + before);
        } catch (Exception ex) {
            System.err.println("[OTA] 从项目备份恢复失败，换回原来的数据：" + ex);
            for (String item : List.of("db", "blobs", "master.key", "install.json")) {
                try {
                    Path cur = dataDir.resolve(item);
                    if (moved.contains(item)) {
                        deleteTree(cur);
                        Files.move(before.resolve(item), cur);
                    }
                } catch (IOException e2) {
                    System.err.println("[OTA] 换回 " + item + " 失败，请到 " + before + " 手动找回：" + e2);
                }
            }
            try {
                ObjectNode last = om.createObjectNode();
                last.put("ok", false);
                last.put("restoredAt", LocalDateTime.now().toString());
                last.put("error", String.valueOf(ex.getMessage()));
                Files.write(restore.resolve("last.json"), om.writeValueAsBytes(last));
                Files.deleteIfExists(marker);
            } catch (IOException ignored) {
                // 写不了结果也不影响启动
            }
        }
        try (Stream<Path> old = Files.list(restore)) {
            List<Path> dirs = old.filter(p -> Files.isDirectory(p) && p.getFileName().toString().startsWith("before-"))
                    .sorted(Comparator.comparing(Path::toString).reversed()).toList();
            for (int i = 2; i < dirs.size(); i++) deleteTree(dirs.get(i));
        } catch (IOException ignored) {
            // 清不掉下次再清
        }
    }

    private static void deleteTree(Path p) throws IOException {
        if (!Files.exists(p)) return;
        try (Stream<Path> all = Files.walk(p)) {
            for (Path f : (Iterable<Path>) all.sorted(Comparator.reverseOrder())::iterator) Files.deleteIfExists(f);
        }
    }
}
