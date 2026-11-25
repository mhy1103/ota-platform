package com.ota.platform.pkg;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/** 更新包文件：读取外部链接、识别文件类型、安全解压。上传、外部链接和校验共用。 */
public final class PackageFiles {

    public static final long MAX_DOWNLOAD = 150L * 1024 * 1024;
    private static final long MAX_UNZIPPED = 600L * 1024 * 1024;
    private static final int MAX_ENTRIES = 20000;
    private static final int MAX_REDIRECTS = 5;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PackageFiles() {}

    /** 下载或解压失败，message 可以直接展示给用户 */
    public static class PackageException extends IOException {
        public PackageException(String message) { super(message); }
    }

    public static URI parseUrl(String url) {
        try {
            URI uri = URI.create(url.trim().replace(" ", "%20"));
            String scheme = uri.getScheme();
            if (("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) && uri.getHost() != null) return uri;
        } catch (Exception ignored) {}
        throw new IllegalArgumentException("不是有效的 http/https 链接");
    }

    /** HTTP 404，调用方可以据此给出更具体的提示 */
    public static class NotFound extends PackageException {
        public NotFound(String message) { super(message); }
    }

    /** 超时、连接被断开这类网络问题，重试可能就好了 */
    public static class Transient extends PackageException {
        public Transient(String message) { super(message); }
    }

    /** 文件内容的 SHA-256（base64url，Expo 清单里的 hash）、MD5（清单里的 key）和大小 */
    public record Digest(String hash, String key, long size) {}

    /** 小文件（metadata.json 之类）直接读到内存 */
    public static byte[] fetchBytes(URI uri, long max) throws PackageException {
        return fetch(uri, max, (in, conn) -> {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            copy(in, out, max, null);
            return out.toByteArray();
        });
    }

    /** 下载进度：开始时给文件大小（不知道是 -1），之后每读到一段给一次字节数 */
    public interface Listener {
        void start(long size);

        void read(int n);
    }

    /** 边下边算哈希，不落盘 */
    public static Digest digest(URI uri, long max) throws PackageException {
        return digest(uri, max, null);
    }

    public static Digest digest(URI uri, long max, Listener listener) throws PackageException {
        return fetch(uri, max, (in, conn) -> {
            try {
                java.security.MessageDigest sha = java.security.MessageDigest.getInstance("SHA-256");
                java.security.MessageDigest md5 = java.security.MessageDigest.getInstance("MD5");
                if (listener != null) listener.start(conn.getContentLengthLong());
                long total = copy(in, OutputStream.nullOutputStream(), max, (buf, n) -> {
                    sha.update(buf, 0, n);
                    md5.update(buf, 0, n);
                    if (listener != null) listener.read(n);
                });
                return new Digest(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(sha.digest()), hex(md5.digest()), total);
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IOException(e);
            }
        });
    }

    private interface Body<T> {
        T read(InputStream in, HttpURLConnection conn) throws IOException;
    }

    private interface Chunk {
        void accept(byte[] buf, int n);
    }

    private static <T> T fetch(URI uri, long max, Body<T> body) throws PackageException {
        URI current = uri;
        HttpURLConnection conn = null;
        boolean ok = false;
        try {
            for (int hop = 0; ; hop++) {
                conn = (HttpURLConnection) URI.create(current.toASCIIString()).toURL().openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(30_000);
                conn.setInstanceFollowRedirects(false);
                conn.setRequestProperty("User-Agent", "OTA-Platform/1.0");
                int code = conn.getResponseCode();
                String location = conn.getHeaderField("Location");
                if (code >= 300 && code < 400 && location != null) {
                    if (hop >= MAX_REDIRECTS) throw new PackageException("跳转次数太多，链接可能有问题");
                    URI next = current.resolve(location.trim().replace(" ", "%20"));
                    if (!"http".equalsIgnoreCase(next.getScheme()) && !"https".equalsIgnoreCase(next.getScheme())) {
                        throw new PackageException("链接跳转到了不支持的地址：" + location);
                    }
                    conn.disconnect();
                    current = next;
                    continue;
                }
                if (code == 404) throw new NotFound("HTTP 404（文件不存在）");
                if (code >= 500) throw new Transient("HTTP " + code + "（服务器出错）");
                if (code != 200) throw new PackageException("HTTP " + code + (code == 403 ? "（无权访问，对象存储需要设为公共读）" : ""));
                break;
            }
            long declared = conn.getContentLengthLong();
            if (declared > max) throw new PackageException("文件 " + size(declared) + "，超过上限 " + size(max));
            T result;
            try (InputStream in = conn.getInputStream()) {
                result = body.read(in, conn);
            }
            ok = true;
            return result;
        } catch (PackageException e) {
            throw e;
        } catch (SocketTimeoutException e) {
            throw new Transient("下载超时，链接响应太慢");
        } catch (UnknownHostException e) {
            throw new PackageException("域名无法解析：" + current.getHost());
        } catch (IOException e) {
            throw new Transient("下载失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        } finally {
            // 完整读完的连接留着给下一个文件用（同一个域名不用每次都重新握手），出错的才断开
            if (conn != null && !ok) conn.disconnect();
        }
    }

    private static long copy(InputStream in, OutputStream out, long max, Chunk chunk) throws IOException {
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        int n;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > max) throw new PackageException("文件超过上限 " + size(max));
            if (chunk != null) chunk.accept(buf, n);
            out.write(buf, 0, n);
        }
        return total;
    }

    private static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }

    public static boolean isZip(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(4);
            return head.length == 4 && head[0] == 'P' && head[1] == 'K' && head[2] == 3 && head[3] == 4;
        }
    }

    /** 不是 ZIP 时猜一下是什么，返回「（是 …）」或空串 */
    public static String guessKind(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(16);
            if (head.length >= 4 && head[0] == (byte) 0xC6 && head[1] == 0x1F && head[2] == (byte) 0xBC && head[3] == 0x03) return "（是 Hermes 字节码 .hbc 单文件）";
            String text = new String(head, StandardCharsets.ISO_8859_1).trim().toLowerCase(Locale.ROOT);
            if (text.startsWith("<!doctype") || text.startsWith("<html")) return "（下载到的是网页，链接可能需要登录或已失效）";
            if (text.startsWith("{") || text.startsWith("[")) return "（是 JSON 文件）";
            return "";
        } catch (IOException e) {
            return "";
        }
    }

    /** 安全解压：防路径穿越，限制文件数和总大小。返回文件数 */
    public static int unzip(Path zip, Path dir) throws IOException {
        int count = 0;
        long total = 0;
        Path base = dir.toAbsolutePath().normalize();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            byte[] buf = new byte[64 * 1024];
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                if (++count > MAX_ENTRIES) throw new PackageException("压缩包里文件太多（超过 " + MAX_ENTRIES + " 个）");
                Path target = base.resolve(entry.getName()).normalize();
                if (!target.startsWith(base) || target.equals(base)) throw new PackageException("压缩包包含非法路径：" + entry.getName());
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    int n;
                    while ((n = zis.read(buf)) > 0) {
                        total += n;
                        if (total > MAX_UNZIPPED) throw new PackageException("解压后超过 " + size(MAX_UNZIPPED) + "，不像是正常的更新包");
                        out.write(buf, 0, n);
                    }
                }
            }
        } catch (ZipException e) {
            throw new PackageException("ZIP 文件损坏：" + e.getMessage());
        }
        return count;
    }

    /** 包的根目录：压缩包根、dist/，或唯一一个含 metadata.json 的子目录 */
    public static Path packageRoot(Path dir) {
        if (Files.isRegularFile(dir.resolve("metadata.json"))) return dir;
        Path dist = dir.resolve("dist");
        if (Files.isRegularFile(dist.resolve("metadata.json"))) return dist;
        try (var list = Files.list(dir)) {
            List<Path> roots = list.filter(p -> Files.isRegularFile(p.resolve("metadata.json"))).toList();
            if (roots.size() == 1) return roots.get(0);
        } catch (IOException ignored) {}
        return dir;
    }

    /** expoConfig.json 里的 runtimeVersion（没有就取 version），读不到返回 null */
    public static String runtimeOf(Path root) {
        Path cfg = root.resolve("expoConfig.json");
        if (!Files.isRegularFile(cfg)) return null;
        try {
            Map<?, ?> config = MAPPER.readValue(cfg.toFile(), Map.class);
            if (config.get("runtimeVersion") instanceof String s && !s.isBlank()) return s;
            if (config.get("version") instanceof String s && !s.isBlank()) return s;
        } catch (Exception ignored) {}
        return null;
    }

    public static String size(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1048576) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1048576.0);
    }

    /** 按段编码，保留 / */
    public static String encodePath(String path) {
        StringBuilder sb = new StringBuilder();
        String[] segs = path.split("/", -1);
        for (int i = 0; i < segs.length; i++) {
            if (i > 0) sb.append('/');
            sb.append(java.net.URLEncoder.encode(segs[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return sb.toString();
    }

    public static void deleteQuietly(Path p) {
        if (p == null) return;
        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
    }

    public static void deleteDirQuietly(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return;
        try (var walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(PackageFiles::deleteQuietly);
        } catch (IOException ignored) {}
    }
}
