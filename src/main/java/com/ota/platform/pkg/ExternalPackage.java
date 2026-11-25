package com.ota.platform.pkg;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 外部链接版本：dist 目录原样放在 CDN / 对象存储上，App 直接从那里下载文件。
 * 服务器只读一遍 metadata.json 和每个文件算出哈希（不保存文件），下发时把这些哈希写进签名后的清单。
 */
@Service
public class ExternalPackage {

    private static final long MAX_JSON = 5L * 1024 * 1024;
    private static final long MAX_TOTAL = 1024L * 1024 * 1024;
    private static final int MAX_FILES = 5000;
    private static final int THREADS = 8;
    /** 名字里带 32 位十六进制的文件：expo export 的 assets/<md5>、index-<md5>.hbc */
    private static final Pattern MD5_NAME = Pattern.compile("(?:^|[/-])([0-9a-f]{32})(?:\\.[A-Za-z0-9]+)?$");

    private final ObjectMapper mapper = new ObjectMapper();
    /**
     * 读过的文件。只记文件名里的 MD5 和算出来的对得上的：这种地址的内容不会变，重新读同一个链接（上次没读完、出错重试）时不用再下载
     */
    private final Map<String, PackageFiles.Digest> seen = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, PackageFiles.Digest> e) {
            return size() > 20_000;
        }
    });

    /** 读取进度，页面轮询用；cancelled 设为 true 后没读完的文件尽快停下 */
    public static final class Progress {
        public volatile int total;
        public final AtomicInteger done = new AtomicInteger();
        public final AtomicLong bytes = new AtomicLong();
        /** 正在下载的文件 → {已读字节, 文件大小（不知道是 -1）} */
        public final Map<String, long[]> reading = new ConcurrentHashMap<>();
        public volatile boolean cancelled;
    }

    /** 从外部链接读到的内容 */
    public record Fetched(String baseUrl, byte[] metadata, byte[] expoConfig, String runtime, Layout layout,
                          Map<String, PackageFiles.Digest> files, long totalBytes) {}

    /** metadata.json 里列出的文件：每个平台的启动包，以及所有资源文件 */
    public record Layout(Map<String, String> bundles, Set<String> assets) {
        public Set<String> allPaths() {
            Set<String> all = new LinkedHashSet<>(bundles.values());
            all.addAll(assets);
            return all;
        }
    }

    /** 单个文件的检查结果，digest 和 error 二选一 */
    public record Probe(String path, PackageFiles.Digest digest, String error) {}

    public Fetched fetch(String input) throws IOException {
        return fetch(input, new Progress());
    }

    public Fetched fetch(String input, Progress progress) throws IOException {
        URI base = baseOf(input);
        byte[] metadata = metadata(base);
        Layout layout = layout(metadata);
        byte[] expoConfig = optional(base, "expoConfig.json");

        List<Probe> probes = probe(base, layout.allPaths(), progress);
        if (progress.cancelled) throw new PackageFiles.PackageException("已取消读取");
        List<String> failed = probes.stream().filter(p -> p.error() != null).map(p -> p.path() + "（" + p.error() + "）").toList();
        if (!failed.isEmpty()) {
            throw new PackageFiles.PackageException("有 " + failed.size() + " 个文件无法从外部链接下载："
                    + String.join("、", failed.subList(0, Math.min(3, failed.size()))) + (failed.size() > 3 ? " 等" : "")
                    + "。请确认 dist 目录是完整上传的，并且可以公开访问");
        }
        Map<String, PackageFiles.Digest> files = new LinkedHashMap<>();
        long total = 0;
        for (Probe p : probes) {
            files.put(p.path(), p.digest());
            total += p.digest().size();
        }
        if (total > MAX_TOTAL) throw new PackageFiles.PackageException("文件总大小 " + PackageFiles.size(total) + "，超过上限 " + PackageFiles.size(MAX_TOTAL));
        return new Fetched(base.toString(), metadata, expoConfig, runtimeOf(expoConfig), layout, files, total);
    }

    /** 读完到真正添加之间，链接里的 dist 可能被换掉：metadata.json、expoConfig.json 和读的时候一样，读好的结果才能用 */
    public boolean unchanged(Fetched f) throws PackageFiles.PackageException {
        URI base = URI.create(f.baseUrl());
        return Arrays.equals(metadata(base), f.metadata()) && Arrays.equals(optional(base, "expoConfig.json"), f.expoConfig());
    }

    /**
     * 找到 dist 根目录：可以填目录本身、目录里的 metadata.json，或目录里某个 _expo/ 下的文件。
     * 压缩包和单个启动包 App 用不了，给出怎么放的提示。
     */
    public URI baseOf(String input) throws PackageFiles.PackageException {
        URI uri = PackageFiles.parseUrl(input);
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        String lower = path.toLowerCase(Locale.ROOT);
        int expo = path.indexOf("/_expo/");
        if (expo >= 0) {
            path = path.substring(0, expo + 1);
        } else if (lower.endsWith(".zip")) {
            throw new PackageFiles.PackageException("这是压缩包链接。App 是一个个文件下载的，用不了压缩包："
                    + "请把 dist 解压后的整个目录上传到 CDN / 对象存储，填这个目录的地址");
        } else if (lower.endsWith("/metadata.json") || lower.endsWith("/expoconfig.json")) {
            path = path.substring(0, path.lastIndexOf('/') + 1);
        } else if (lower.endsWith(".hbc") || lower.endsWith(".bundle") || lower.endsWith(".js")) {
            throw new PackageFiles.PackageException("这是单个启动包文件的链接。热更新还需要 metadata.json 和图片、字体等资源："
                    + "请把 dist 整个目录上传到 CDN / 对象存储，填这个目录的地址");
        }
        if (!path.endsWith("/")) path += "/";
        return URI.create(uri.getScheme().toLowerCase(Locale.ROOT) + "://" + uri.getRawAuthority() + path);
    }

    public byte[] metadata(URI base) throws PackageFiles.PackageException {
        try {
            return PackageFiles.fetchBytes(base.resolve("metadata.json"), MAX_JSON);
        } catch (PackageFiles.NotFound e) {
            throw new PackageFiles.PackageException("找不到 " + base + "metadata.json。外部链接要填 npx expo export 导出的 dist 目录地址"
                    + "（浏览器能打开「链接/metadata.json」就对了）");
        } catch (PackageFiles.PackageException e) {
            throw new PackageFiles.PackageException("读取 metadata.json 失败：" + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    public Layout layout(byte[] metadata) throws PackageFiles.PackageException {
        Map<String, Object> m;
        try {
            m = mapper.readValue(metadata, Map.class);
        } catch (IOException e) {
            throw new PackageFiles.PackageException("metadata.json 不是有效的 JSON，链接指向的可能不是 dist 目录");
        }
        Map<String, Map<String, Object>> platforms = new LinkedHashMap<>();
        Object fm = m.get("fileMetadata");
        if (fm instanceof Map<?, ?> map) {
            map.forEach((k, v) -> { if (v instanceof Map<?, ?> pm) platforms.put(String.valueOf(k), (Map<String, Object>) pm); });
        } else if (fm instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> pm) platforms.put(String.valueOf(pm.get("platform")), (Map<String, Object>) pm);
            }
        }
        Map<String, String> bundles = new LinkedHashMap<>();
        Set<String> assets = new LinkedHashSet<>();
        for (Map.Entry<String, Map<String, Object>> e : platforms.entrySet()) {
            if (!(e.getValue().get("bundle") instanceof String bundle)) continue;
            bundles.put(e.getKey(), checkPath(bundle));
            if (e.getValue().get("assets") instanceof List<?> list) {
                for (Object a : list) {
                    if (a instanceof Map<?, ?> am && am.get("path") instanceof String p) assets.add(checkPath(p));
                }
            }
        }
        if (bundles.isEmpty()) throw new PackageFiles.PackageException("metadata.json 里没有任何平台的启动包信息");
        if (bundles.size() + assets.size() > MAX_FILES) throw new PackageFiles.PackageException("metadata.json 里的文件超过 " + MAX_FILES + " 个");
        return new Layout(bundles, assets);
    }

    /** 并发下载并算哈希，每个文件单独给出结果 */
    public List<Probe> probe(URI base, Collection<String> paths) throws IOException {
        return probe(base, paths, new Progress());
    }

    public List<Probe> probe(URI base, Collection<String> paths, Progress progress) throws IOException {
        progress.total = paths.size();
        if (paths.isEmpty()) return List.of();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(THREADS, paths.size()));
        try {
            List<Future<Probe>> futures = new ArrayList<>();
            for (String p : paths) {
                futures.add(pool.submit(() -> {
                    URI uri = base.resolve(PackageFiles.encodePath(p));
                    PackageFiles.Digest known = seen.get(uri.toString());
                    if (known != null) {
                        progress.bytes.addAndGet(known.size());
                        progress.done.incrementAndGet();
                        return new Probe(p, known, null);
                    }
                    long[] slot = {0, -1};
                    progress.reading.put(p, slot);
                    PackageFiles.Listener listener = new PackageFiles.Listener() {
                        @Override
                        public void start(long size) {
                            slot[1] = size;
                        }

                        @Override
                        public void read(int n) {
                            if (progress.cancelled) throw new CancellationException();
                            slot[0] += n;
                            progress.bytes.addAndGet(n);
                        }
                    };
                    try {
                        PackageFiles.Digest d;
                        // 慢链接上偶尔断一下、超时一次很常见：这类错误同一个文件再试两次，不要因为一个文件让整个链接读失败
                        for (int attempt = 1; ; attempt++) {
                            if (progress.cancelled) return new Probe(p, null, "已取消");
                            try {
                                d = PackageFiles.digest(uri, PackageFiles.MAX_DOWNLOAD, listener);
                                break;
                            } catch (PackageFiles.Transient e) {
                                if (attempt >= 3) throw e;
                                progress.bytes.addAndGet(-slot[0]);
                                slot[0] = 0;
                                Thread.sleep(1000L * attempt);
                            }
                        }
                        Matcher m = MD5_NAME.matcher(p);
                        if (m.find() && m.group(1).equalsIgnoreCase(d.key())) seen.put(uri.toString(), d);
                        return new Probe(p, d, null);
                    } catch (PackageFiles.PackageException e) {
                        return new Probe(p, null, e.getMessage());
                    } catch (CancellationException | InterruptedException e) {
                        return new Probe(p, null, "已取消");
                    } finally {
                        progress.reading.remove(p);
                        progress.done.incrementAndGet();
                    }
                }));
            }
            List<Probe> out = new ArrayList<>();
            for (Future<Probe> f : futures) out.add(f.get());
            return out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("下载被中断");
        } catch (ExecutionException e) {
            throw new IOException("下载失败：" + e.getCause().getMessage());
        } finally {
            pool.shutdownNow();
        }
    }

    public String runtimeOf(byte[] expoConfig) {
        if (expoConfig == null) return null;
        try {
            Map<?, ?> config = mapper.readValue(expoConfig, Map.class);
            if (config.get("runtimeVersion") instanceof String s && !s.isBlank()) return s;
            if (config.get("version") instanceof String s && !s.isBlank()) return s;
        } catch (IOException ignored) {}
        return null;
    }

    private byte[] optional(URI base, String name) throws PackageFiles.PackageException {
        try {
            return PackageFiles.fetchBytes(base.resolve(name), MAX_JSON);
        } catch (PackageFiles.NotFound e) {
            return null;
        } catch (PackageFiles.PackageException e) {
            throw new PackageFiles.PackageException("读取 " + name + " 失败：" + e.getMessage());
        }
    }

    private static String checkPath(String p) throws PackageFiles.PackageException {
        boolean bad = p.isBlank() || p.startsWith("/") || p.contains("\\") || p.contains("://")
                || Arrays.asList(p.split("/")).contains("..");
        if (bad) throw new PackageFiles.PackageException("metadata.json 里有非法文件路径：" + p);
        return p;
    }
}
