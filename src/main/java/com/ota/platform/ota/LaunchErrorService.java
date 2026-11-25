package com.ota.platform.ota;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 记录 App 启动崩溃的错误信息。同一台设备同样的错误 6 小时内只记一次，每个 App 只留 30 天 */
@Service
public class LaunchErrorService {

    private static final Duration DEDUPE = Duration.ofHours(6);
    private static final Duration KEEP = Duration.ofDays(30);

    private final LaunchErrorRepo repo;
    private final Map<String, Instant> recent = new ConcurrentHashMap<>();

    public LaunchErrorService(LaunchErrorRepo repo) {
        this.repo = repo;
    }

    @Transactional
    public void record(Long appId, String releaseId, String clientId, String platform, String runtime, String message) {
        if (message == null || message.isBlank()) return;
        String key = appId + "|" + clientId + "|" + message.hashCode();
        Instant now = Instant.now();
        Instant last = recent.get(key);
        if (last != null && last.isAfter(now.minus(DEDUPE))) return;
        if (recent.size() > 50_000) recent.clear();
        recent.put(key, now);
        repo.save(new LaunchError(appId, releaseId, clientId, platform, runtime, message.trim()));
        if (Math.floorMod(key.hashCode(), 50) == 0) repo.deleteOlder(appId, now.minus(KEEP));
    }

    public List<LaunchError> recent(Long appId) {
        return repo.findTop30ByAppIdOrderByCreatedAtDesc(appId);
    }

    public List<LaunchError> forRelease(String releaseId) {
        return repo.findTop10ByReleaseIdOrderByCreatedAtDesc(releaseId);
    }

    /** 一类启动失败：错误的第一行、出现几次、各平台几次、最近一次 */
    public record Reason(String message, long count, long ios, long android, Instant last) {}

    /** 这个版本的启动失败按错误的第一行归类，多的排前面（看最近 2000 条） */
    public List<Reason> reasons(String releaseId) {
        Map<String, long[]> counts = new java.util.LinkedHashMap<>();
        Map<String, Instant> last = new java.util.HashMap<>();
        for (LaunchError e : repo.findTop2000ByReleaseIdOrderByCreatedAtDesc(releaseId)) {
            String key = headline(e.getMessage());
            long[] c = counts.computeIfAbsent(key, k -> new long[3]);
            c[0]++;
            if ("ios".equalsIgnoreCase(e.getPlatform())) c[1]++;
            else if ("android".equalsIgnoreCase(e.getPlatform())) c[2]++;
            last.merge(key, e.getCreatedAt(), (a, b) -> a.isAfter(b) ? a : b);
        }
        return counts.entrySet().stream()
                .map(x -> new Reason(x.getKey(), x.getValue()[0], x.getValue()[1], x.getValue()[2], last.get(x.getKey())))
                .sorted(java.util.Comparator.comparingLong(Reason::count).reversed())
                .toList();
    }

    /** 堆栈里的一帧：at 函数 (文件:行:列)、at 文件:行:列；Hermes 的是 at 函数 (address at 文件:行:列) */
    private static final java.util.regex.Pattern FRAME = java.util.regex.Pattern.compile(
            "\\s+at\\s+(?:[^\\s()]+\\s*)?\\([^)]*:\\d+(?::\\d+)?\\)|\\s+at\\s+[^\\s()]*:\\d+(?::\\d+)?");

    /** 错误信息的第一行，去掉堆栈（同一个错误在不同位置抛出算一类），空白合并成一个，最长 160 个字 */
    static String headline(String message) {
        String s = message == null ? "" : message.strip();
        int nl = s.indexOf('\n');
        if (nl >= 0) s = s.substring(0, nl);
        java.util.regex.Matcher m = FRAME.matcher(s);
        if (m.find() && m.start() > 0) s = s.substring(0, m.start());
        s = s.replaceAll("\\s+", " ").strip();
        return s.length() > 160 ? s.substring(0, 160) + "…" : s;
    }

    public long countSince(Long appId, Duration window) {
        return repo.countByAppIdAndCreatedAtAfter(appId, Instant.now().minus(window));
    }

    @Transactional
    public void dropApp(Long appId) {
        repo.deleteByAppId(appId);
    }
}
