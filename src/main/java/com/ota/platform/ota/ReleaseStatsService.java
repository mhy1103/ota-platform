package com.ota.platform.ota;

import com.ota.platform.app.AppService;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 版本的更新漏斗和流量计数。检查更新、下载启动包时只在内存里加数，每 10 秒（或页面要看时）写一次库。
 * 写库时顺便检查启动失败率，超过阈值的版本发一次告警。
 */
@Service
public class ReleaseStatsService {

    private static final Logger log = LoggerFactory.getLogger(ReleaseStatsService.class);
    /** 至少这么多台设备失败、且失败率不低于 ALERT_RATE 才告警 */
    public static final int ALERT_MIN_FAILED = 3;
    public static final int ALERT_RATE = 5;

    private static final int OFFERED = 0, INSTALLED = 1, FAILED = 2, PATCH = 3, FULL = 4, CDN = 5, SERVED = 6, SAVED = 7;

    private final ReleaseStatRepo repo;
    private final ReleaseRepository releases;
    private final AppService apps;
    private final OtaEvents events;
    private final ReleaseGuard guard;
    private final Object lock = new Object();
    private Map<String, long[]> pending = new HashMap<>();
    private final ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "release-stats");
        t.setDaemon(true);
        return t;
    });

    public ReleaseStatsService(ReleaseStatRepo repo, ReleaseRepository releases, AppService apps, OtaEvents events, ReleaseGuard guard) {
        this.repo = repo;
        this.releases = releases;
        this.apps = apps;
        this.events = events;
        this.guard = guard;
        flusher.scheduleWithFixedDelay(this::flushQuietly, 10, 10, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stop() {
        flusher.shutdownNow();
        flushQuietly();
    }

    public void offered(String releaseId) { add(releaseId, OFFERED, 1); }
    public void installed(String releaseId) { add(releaseId, INSTALLED, 1); }
    public void failed(String releaseId) { add(releaseId, FAILED, 1); }

    /** 下载计数：patch 启动包增量下载（saved 是比完整文件少传的字节），full 启动包完整下载，cdn 跳转到对象存储，asset 其他资源只记流量 */
    public void download(String releaseId, String kind, long bytes, long saved) {
        if (releaseId == null) return;
        synchronized (lock) {
            long[] a = pending.computeIfAbsent(releaseId, k -> new long[8]);
            switch (kind) {
                case "patch" -> a[PATCH]++;
                case "cdn" -> a[CDN]++;
                case "full" -> a[FULL]++;
                default -> { }
            }
            a[SERVED] += bytes;
            a[SAVED] += saved;
        }
    }

    private void add(String releaseId, int idx, long v) {
        if (releaseId == null || releaseId.length() > 36) return;
        synchronized (lock) {
            pending.computeIfAbsent(releaseId, k -> new long[8])[idx] += v;
        }
    }

    /** 读之前先把内存里的数写进去，页面上看到的是最新的 */
    public Map<String, ReleaseStat> get(Collection<String> releaseIds) {
        flushQuietly();
        Map<String, ReleaseStat> out = new HashMap<>();
        if (releaseIds.isEmpty()) return out;
        for (ReleaseStat s : repo.findAllById(releaseIds)) out.put(s.getReleaseId(), s);
        return out;
    }

    public ReleaseStat get(String releaseId) {
        return get(List.of(releaseId)).getOrDefault(releaseId, new ReleaseStat(releaseId));
    }

    public void drop(Collection<String> releaseIds) {
        flushQuietly();
        List<ReleaseStat> found = repo.findAllById(releaseIds);
        if (!found.isEmpty()) repo.deleteAll(found);
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (Exception e) {
            log.warn("写版本统计失败：{}", e.toString());
        }
    }

    private synchronized void flush() {
        Map<String, long[]> batch;
        synchronized (lock) {
            if (pending.isEmpty()) return;
            batch = pending;
            pending = new HashMap<>();
        }
        Map<String, ReleaseStat> existing = new HashMap<>();
        for (ReleaseStat s : repo.findAllById(batch.keySet())) existing.put(s.getReleaseId(), s);
        List<ReleaseStat> changed = new ArrayList<>();
        List<ReleaseStat> failing = new ArrayList<>();
        List<ReleaseStat> newFailures = new ArrayList<>();
        for (Map.Entry<String, long[]> e : batch.entrySet()) {
            if (!existing.containsKey(e.getKey()) && releases.findById(e.getKey()).isEmpty()) continue;
            ReleaseStat s = existing.computeIfAbsent(e.getKey(), ReleaseStat::new);
            long[] a = e.getValue();
            s.add(a[OFFERED], a[INSTALLED], a[FAILED], a[PATCH], a[FULL], a[CDN], a[SERVED], a[SAVED]);
            changed.add(s);
            if (a[FAILED] > 0) newFailures.add(s);
            if (a[FAILED] > 0 && s.getAlertedAt() == null && s.getFailed() >= ALERT_MIN_FAILED && s.getFailRate() >= ALERT_RATE) {
                s.setAlertedAt(Instant.now());
                failing.add(s);
            }
        }
        repo.saveAll(changed);
        for (ReleaseStat s : failing) {
            releases.findById(s.getReleaseId()).ifPresent(r -> apps.findById(r.getAppId()).ifPresent(app -> events.launchAlert(app, r, s)));
        }
        for (ReleaseStat s : newFailures) {
            try {
                guard.check(s);
            } catch (Exception ex) {
                log.warn("检查自动回滚失败：{}", ex.toString());
            }
        }
    }

    /** 这个版本是不是启动失败率过高（页面上标红、提示回滚） */
    public static boolean alarming(ReleaseStat s) {
        return s != null && s.getFailed() >= ALERT_MIN_FAILED && s.getFailRate() >= ALERT_RATE;
    }

    public Release releaseOf(String id) {
        return releases.findById(id).orElse(null);
    }
}
