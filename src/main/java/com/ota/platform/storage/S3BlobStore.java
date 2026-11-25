package com.ota.platform.storage;

import com.ota.platform.common.Hashes;
import com.ota.platform.config.OtaProperties;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 存在 S3 兼容对象存储里，对象名是「前缀 + SHA-256」。App 从 public-url（一般是 CDN）下载。 */
public class S3BlobStore implements BlobStore {

    private static final String CACHE_CONTROL = "public, max-age=31536000, immutable";

    private final S3Client client;
    private final String prefix;
    private final String publicBase;
    /** 已确认存在的对象，内容按哈希寻址永远不变，可以一直缓存 */
    private final Set<String> known = ConcurrentHashMap.newKeySet();

    public S3BlobStore(OtaProperties.S3 cfg) {
        if (cfg.getBucket() == null || cfg.getBucket().isBlank() || cfg.getEndpoint() == null || cfg.getEndpoint().isBlank()) {
            throw new IllegalStateException("ota.storage.type=s3 时要配置 ota.storage.s3.endpoint 和 bucket");
        }
        if (cfg.getAccessKey() == null || cfg.getAccessKey().isBlank() || cfg.getSecretKey() == null || cfg.getSecretKey().isBlank()) {
            throw new IllegalStateException("ota.storage.type=s3 时要配置 access-key 和 secret-key");
        }
        this.client = new S3Client(cfg.getEndpoint(), cfg.getRegion(), cfg.getBucket(), cfg.getAccessKey(), cfg.getSecretKey(), cfg.isPathStyle());
        String p = cfg.getPrefix().trim().replaceAll("^/+", "");
        this.prefix = p.isEmpty() || p.endsWith("/") ? p : p + "/";
        String pub = cfg.getPublicUrl() == null ? "" : cfg.getPublicUrl().trim().replaceAll("/+$", "");
        this.publicBase = pub.isEmpty() ? null : pub;
    }

    private String key(String sha256) {
        return prefix + sha256;
    }

    @Override
    public boolean exists(String sha256) {
        if (!Hashes.isSha256Hex(sha256)) return false;
        if (known.contains(sha256)) return true;
        try {
            int status = client.head(key(sha256));
            if (status == 200) {
                known.add(sha256);
                return true;
            }
            if (status == 404) return false;
            throw new IllegalStateException("对象存储返回 HTTP " + status + "，检查 endpoint、bucket 和密钥");
        } catch (IOException e) {
            throw new IllegalStateException("连不上对象存储：" + e.getMessage(), e);
        }
    }

    @Override
    public void put(String sha256, Path verifiedFile) throws IOException {
        try {
            client.put(key(sha256), verifiedFile, sha256, "application/octet-stream", CACHE_CONTROL);
            known.add(sha256);
        } finally {
            Files.deleteIfExists(verifiedFile);
        }
    }

    @Override
    public InputStream open(String sha256) throws IOException {
        HttpResponse<InputStream> res = client.get(key(sha256));
        if (res.statusCode() == 200) return res.body();
        String body;
        try (InputStream in = res.body()) {
            body = new String(in.readNBytes(4096), StandardCharsets.UTF_8);
        }
        if (res.statusCode() == 404 && !body.contains("NoSuchBucket")) throw new NoSuchFileException(sha256);
        throw new ReadFailed(res.statusCode(), "读取失败：" + client.explain(res.statusCode(), body));
    }

    /** 试读一个文件看存储连不连得上：读得到就返回，文件不在抛 NoSuchFileException，存储回了错误抛 ReadFailed，网络不通抛 IOException */
    public void check(String sha256) throws IOException {
        HttpResponse<InputStream> res = client.get(key(sha256), Duration.ofSeconds(20));
        try (InputStream in = res.body()) {
            if (res.statusCode() == 200) {
                in.read();
                return;
            }
            String body = new String(in.readNBytes(4096), StandardCharsets.UTF_8);
            if (res.statusCode() == 404 && !body.contains("NoSuchBucket")) throw new NoSuchFileException(sha256);
            throw new ReadFailed(res.statusCode(), "读取失败：" + client.explain(res.statusCode(), body));
        }
    }

    /** 对象存储回了错误状态，不是网络问题 */
    public static final class ReadFailed extends IOException {
        private final int status;

        ReadFailed(int status, String message) {
            super(message);
            this.status = status;
        }

        /** 3xx、4xx（408 超时、429 限流除外）是存储桶、权限或者文件本身的问题，再试也一样 */
        public boolean definite() {
            return status < 500 && status != 408 && status != 429;
        }
    }

    /** App 不带签名直接从下载地址拿文件：试一下能不能下到。能下返回 null，不能下返回原因 */
    public String publicProblem(String sha256, byte[] expected) {
        String url = publicUrl(sha256);
        try {
            HttpResponse<byte[]> res = ANON.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() == 200) return Arrays.equals(res.body(), expected) ? null : url + " 下载到的内容不对，检查 CDN 回源设置";
            return url + " 返回 HTTP " + res.statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return url + " 访问被中断";
        } catch (Exception e) {
            return url + " 访问失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    private static final HttpClient ANON = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public void delete(String sha256) throws IOException {
        client.delete(key(sha256));
        known.remove(sha256);
    }

    @Override
    public Path localPath(String sha256) {
        return null;
    }

    @Override
    public String publicUrl(String sha256) {
        return publicBase != null ? publicBase + "/" + key(sha256) : client.objectUrl(key(sha256));
    }

    @Override
    public String describe() {
        return "对象存储 " + client.describe() + (publicBase != null ? "，下载地址 " + publicBase : "");
    }
}
