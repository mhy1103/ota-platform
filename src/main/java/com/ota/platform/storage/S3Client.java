package com.ota.platform.storage;

import com.ota.platform.common.Hashes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * 最小的 S3 客户端：PUT / GET / HEAD 单个对象，AWS Signature Version 4 签名。
 * 阿里云 OSS、腾讯云 COS、七牛 Kodo、MinIO、Cloudflare R2、AWS S3 都兼容这套协议。
 */
public class S3Client {

    static final String EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
    private static final DateTimeFormatter AMZ_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final DateTimeFormatter DATE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final URI endpoint;
    private final String region;
    private final String bucket;
    private final String accessKey;
    private final String secretKey;
    private final boolean pathStyle;
    // HTTP/1.1：http:// 的自建存储遇到 HTTP/2 升级请求（h2c）有的会出错
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    /**
     * 密钥、存储桶、服务地址里不会有空格：从网页、聊天软件复制时带上的空格、不换行空格、零宽字符一律去掉，
     * 不然 Access Key 多了一个看不见的字符，对象存储就说不认识这个 Access Key
     */
    public static String clean(String s) {
        return s == null ? "" : s.replaceAll("[\\s\\p{Z}\\u200B-\\u200D\\u2060\\uFEFF]+", "");
    }

    /** 服务地址格式不对时抛 IllegalArgumentException，消息直接给页面看 */
    public S3Client(String endpoint, String region, String bucket, String accessKey, String secretKey, boolean pathStyle) {
        this.bucket = clean(bucket);
        String e = clean(endpoint).replaceAll("/+$", "");
        if (!e.matches("(?i)^https?://.*")) e = "https://" + e;
        URI u;
        try {
            u = URI.create(e);
        } catch (IllegalArgumentException bad) {
            u = null;
        }
        if (u == null || u.getHost() == null) {
            throw new IllegalArgumentException("服务地址格式不对：" + endpoint + "，要填成 https://oss-cn-hangzhou.aliyuncs.com 这样");
        }
        String host = u.getHost();
        // 控制台上复制的「存储桶域名」（bucket.oss-cn-hangzhou.aliyuncs.com）当服务地址填了：去掉前面的存储桶名
        String rest = host.toLowerCase(Locale.ROOT).startsWith(this.bucket.toLowerCase(Locale.ROOT) + ".") ? host.substring(this.bucket.length() + 1) : "";
        if (!pathStyle && rest.chars().filter(c -> c == '.').count() >= 2) {
            host = rest;
            u = URI.create(u.getScheme() + "://" + host + (u.getPort() == -1 ? "" : ":" + u.getPort()) + (u.getRawPath() == null ? "" : u.getRawPath()));
        }
        this.endpoint = u;
        this.region = clean(region).isEmpty() ? "us-east-1" : clean(region);
        this.accessKey = clean(accessKey);
        this.secretKey = clean(secretKey);
        // IP、localhost 这类地址前面拼不了存储桶名，只能用路径风格
        this.pathStyle = pathStyle || host.equalsIgnoreCase("localhost") || !host.contains(".") || host.contains(":")
                || host.matches("\\d{1,3}(\\.\\d{1,3}){3}");
    }

    /** 对象的直接地址（不带签名），bucket 设为公共读时可以直接下载 */
    public String objectUrl(String key) {
        return endpoint.getScheme() + "://" + host() + canonicalPath(key);
    }

    private static final Duration TIMEOUT = Duration.ofMinutes(5);

    public int head(String key) throws IOException {
        return send("HEAD", key, HttpRequest.BodyPublishers.noBody(), EMPTY_SHA256, Map.of(), HttpResponse.BodyHandlers.discarding(), TIMEOUT).statusCode();
    }

    public void put(String key, Path file, String sha256Hex, String contentType, String cacheControl) throws IOException {
        Map<String, String> headers = new TreeMap<>();
        if (contentType != null) headers.put("content-type", contentType);
        if (cacheControl != null) headers.put("cache-control", cacheControl);
        // 大文件按每秒至少 64 KB 算超时：服务器出口带宽小（宝塔机器常见几 Mbps）时，几百 MB 的安装包 5 分钟传不完
        Duration timeout = TIMEOUT.plusSeconds(Files.size(file) / (64 * 1024));
        HttpResponse<String> res = send("PUT", key, HttpRequest.BodyPublishers.ofFile(file), sha256Hex, headers,
                HttpResponse.BodyHandlers.ofString(), timeout);
        if (res.statusCode() / 100 != 2) throw new IOException("上传失败：" + explain(res.statusCode(), res.body()));
    }

    public void delete(String key) throws IOException {
        HttpResponse<String> res = send("DELETE", key, HttpRequest.BodyPublishers.noBody(), EMPTY_SHA256, Map.of(),
                HttpResponse.BodyHandlers.ofString(), TIMEOUT);
        if (res.statusCode() / 100 != 2 && res.statusCode() != 404) throw new IOException("删除失败：" + explain(res.statusCode(), res.body()));
    }

    public HttpResponse<InputStream> get(String key) throws IOException {
        return get(key, TIMEOUT);
    }

    public HttpResponse<InputStream> get(String key, Duration timeout) throws IOException {
        return send("GET", key, HttpRequest.BodyPublishers.noBody(), EMPTY_SHA256, Map.of(), HttpResponse.BodyHandlers.ofInputStream(), timeout);
    }

    /** 对象存储返回的错误（XML 里的 Code、Message）换成看得懂的说明 */
    String explain(int status, String body) {
        String code = tag(body, "Code"), msg = tag(body, "Message");
        switch (code) {
            case "InvalidAccessKeyId": {
                // 填反了时 Access Key 框里是密钥，提示里不能露出它的头尾
                String id = keyIdPrefix(secretKey);
                if (id != null && keyIdPrefix(accessKey) == null) {
                    return "Access Key 和 Secret Key 填反了：" + id + " 开头的那串要填在 Access Key，另一串填在 Secret Key";
                }
                return "Access Key 不对，对象存储不认识「" + masked(accessKey) + "」（" + accessKey.length()
                        + " 位）。看看是不是填错了：腾讯云 COS 填 SecretId（AKID 开头），阿里云 OSS 填 AccessKey ID（LTAI 开头）";
            }
            case "SignatureDoesNotMatch": return "Secret Key 不对（签名对不上），检查有没有复制全、多了空格";
            case "NoSuchBucket": return "存储桶 " + bucket + " 不存在：检查存储桶名，以及服务地址是不是存储桶所在地域的";
            case "AccessDenied": return "没有权限（AccessDenied）：给这个 Access Key 开通这个存储桶的读写权限";
            case "RequestTimeTooSkewed": return "服务器时间不准，和对象存储差太多：先校准服务器时间（开启 NTP 时间同步）";
            case "SecondLevelDomainForbidden": return "这家对象存储不支持路径风格访问：取消勾选「路径风格访问」";
            case "InvalidBucketName": return "存储桶名格式不对：" + bucket;
            case "AuthorizationHeaderMalformed": {
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("expecting '([^']+)'").matcher(msg);
                if (m.find()) return "区域不对：区域要填 " + m.group(1);
                break;
            }
            case "PermanentRedirect": return "存储桶不在这个服务地址对应的地域：换成存储桶所在地域的服务地址";
            default: break;
        }
        if (status == 301 || status == 307) return "存储桶不在这个服务地址对应的地域：换成存储桶所在地域的服务地址";
        return "HTTP " + status + (code.isEmpty() ? "" : " " + code) + (msg.isEmpty() ? (code.isEmpty() ? " " + brief(body) : "") : "：" + brief(msg));
    }

    /** 各家 Access Key 的固定开头：腾讯云 SecretId 是 AKID，阿里云 AccessKey ID 是 LTAI，AWS 是 AKIA / ASIA */
    private static String keyIdPrefix(String key) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(AKID|LTAI|AKIA|ASIA)[A-Za-z0-9]{12,}$").matcher(key);
        return m.matches() ? m.group(1) : null;
    }

    /** 提示里只露出头尾，够认出填的是哪个就行 */
    private static String masked(String key) {
        return key.length() <= 8 ? key.charAt(0) + "…" : key.substring(0, 6) + "…" + key.substring(key.length() - 4);
    }

    private static String tag(String xml, String name) {
        if (xml == null) return "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("<" + name + ">([^<]*)</" + name + ">").matcher(xml);
        return m.find() ? m.group(1).trim() : "";
    }

    /** 网络层的错误：Java 给的多半只有类名，换成连的是哪个地址、可能是什么原因 */
    private IOException explain(IOException e, String host) {
        if (causedBy(e, java.net.UnknownHostException.class) || causedBy(e, java.nio.channels.UnresolvedAddressException.class)) {
            return new IOException("找不到 " + host + "（域名解析失败），检查服务地址有没有写错", e);
        }
        if (causedBy(e, java.net.http.HttpConnectTimeoutException.class)) return new IOException("连接 " + host + " 超时：检查服务地址、端口和防火墙", e);
        if (causedBy(e, java.net.http.HttpTimeoutException.class)) return new IOException("对象存储 " + host + " 太久没有响应", e);
        if (causedBy(e, javax.net.ssl.SSLException.class)) {
            return new IOException("和 " + host + " 的 HTTPS 连接失败：服务地址填地域节点，不要带存储桶名；自建存储没有 HTTPS 证书就用 http:// 开头", e);
        }
        if (causedBy(e, java.net.ConnectException.class)) return new IOException("连不上 " + host + "：检查服务地址、端口，以及服务器能不能访问这个地址", e);
        return e.getMessage() == null ? new IOException("连接 " + host + " 失败：" + e.getClass().getSimpleName(), e) : e;
    }

    private static boolean causedBy(Throwable e, Class<? extends Throwable> type) {
        for (Throwable t = e; t != null; t = t.getCause()) if (type.isInstance(t)) return true;
        return false;
    }

    private <T> HttpResponse<T> send(String method, String key, HttpRequest.BodyPublisher body, String payloadSha256,
                                     Map<String, String> extraHeaders, HttpResponse.BodyHandler<T> handler, Duration timeout) throws IOException {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        String amzDate = AMZ_DATE.format(now);
        String dateStamp = DATE_STAMP.format(now);
        String path = canonicalPath(key);
        String host = host();

        TreeMap<String, String> signed = new TreeMap<>(extraHeaders);
        signed.put("host", host);
        signed.put("x-amz-content-sha256", payloadSha256);
        signed.put("x-amz-date", amzDate);

        StringBuilder canonicalHeaders = new StringBuilder();
        for (Map.Entry<String, String> h : signed.entrySet()) {
            canonicalHeaders.append(h.getKey()).append(':').append(h.getValue().trim()).append('\n');
        }
        String signedHeaders = String.join(";", signed.keySet());
        String canonicalRequest = method + "\n" + path + "\n\n" + canonicalHeaders + "\n" + signedHeaders + "\n" + payloadSha256;
        String scope = dateStamp + "/" + region + "/s3/aws4_request";
        String stringToSign = "AWS4-HMAC-SHA256\n" + amzDate + "\n" + scope + "\n"
                + Hashes.hex(Hashes.sha256().digest(canonicalRequest.getBytes(StandardCharsets.UTF_8)));
        byte[] kDate = hmac(("AWS4" + secretKey).getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = hmac(kDate, region);
        byte[] kService = hmac(kRegion, "s3");
        byte[] kSigning = hmac(kService, "aws4_request");
        String signature = Hashes.hex(hmac(kSigning, stringToSign));
        String authorization = "AWS4-HMAC-SHA256 Credential=" + accessKey + "/" + scope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest.Builder b;
        try {
            b = HttpRequest.newBuilder(URI.create(endpoint.getScheme() + "://" + host + path));
        } catch (IllegalArgumentException e) {
            throw new IOException("服务地址和存储桶拼不成合法的地址（" + host + "）：检查存储桶名，自建存储勾选「路径风格访问」");
        }
        b.timeout(timeout)
                .method(method, body)
                .header("Authorization", authorization)
                .header("x-amz-content-sha256", payloadSha256)
                .header("x-amz-date", amzDate);
        extraHeaders.forEach(b::header);
        try {
            return http.send(b.build(), handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("对象存储请求被中断");
        } catch (IOException e) {
            throw explain(e, host);
        }
    }

    /** Host 头：虚拟主机风格是 bucket.endpoint，路径风格是 endpoint；非默认端口要带上 */
    private String host() {
        String h = endpoint.getHost();
        int port = endpoint.getPort();
        boolean defaultPort = port == -1 || ("https".equals(endpoint.getScheme()) && port == 443) || ("http".equals(endpoint.getScheme()) && port == 80);
        String base = defaultPort ? h : h + ":" + port;
        return pathStyle ? base : bucket + "." + base;
    }

    private String canonicalPath(String key) {
        StringBuilder sb = new StringBuilder();
        String prefixPath = endpoint.getRawPath() == null ? "" : endpoint.getRawPath();
        sb.append(prefixPath);
        if (pathStyle) sb.append('/').append(encode(bucket));
        for (String seg : key.split("/", -1)) sb.append('/').append(encode(seg));
        return sb.toString();
    }

    /** RFC 3986 编码，S3 签名要求 */
    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20").replace("*", "%2A").replace("%7E", "~");
    }

    private static byte[] hmac(byte[] key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String brief(String body) {
        if (body == null) return "";
        String b = body.replaceAll("\\s+", " ").trim();
        return b.length() > 200 ? b.substring(0, 200) + "…" : b;
    }

    public String describe() {
        return bucket + " @ " + endpoint.getHost() + (pathStyle ? "（路径风格）" : "") + " · " + region.toLowerCase(Locale.ROOT);
    }
}
