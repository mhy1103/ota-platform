package com.ota.platform.stats;

import com.ota.platform.config.OtaProperties;
import com.ota.platform.ota.LaunchErrorService;
import com.ota.platform.ota.ReleaseStatsService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设备统计：设备每次检查更新时记下它正在运行的版本，并据此统计每个版本的推送、安装、启动失败。
 * 异步写库，同一台设备状态没变化时 10 分钟内只写一次。
 */
@Service
public class StatsService implements com.ota.platform.app.AppPurge {

    private static final Logger log = LoggerFactory.getLogger(StatsService.class);
    private static final long THROTTLE_MS = 10 * 60 * 1000;
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    public static final Duration ACTIVE_WINDOW = Duration.ofDays(30);
    /** 设备运行的是安装包内置的版本（当前版本 ID 等于内置版本 ID） */
    public static final String EMBEDDED = "embedded";

    private final DeviceSeenRepository repo;
    private final DeviceDailyRepo daily;
    private final ZoneId zone;
    private final ReleaseStatsService releaseStats;
    private final LaunchErrorService launchErrors;
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "device-stats");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, Long> lastWrite = new ConcurrentHashMap<>();

    public StatsService(DeviceSeenRepository repo, DeviceDailyRepo daily, OtaProperties props,
                        ReleaseStatsService releaseStats, LaunchErrorService launchErrors) {
        this.repo = repo;
        this.daily = daily;
        ZoneId z;
        try {
            z = ZoneId.of(props.getDisplayZone());
        } catch (Exception e) {
            z = ZoneId.of("Asia/Shanghai");
        }
        this.zone = z;
        this.releaseStats = releaseStats;
        this.launchErrors = launchErrors;
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }

    /** 一次检查更新：设备当前版本、这次发给它的版本（没发是 null）、它报告的启动失败版本和崩溃信息，以及 App 说明的崩溃版本 */
    public record Check(Long appId, String clientId, String platform, String runtime, String channel, String currentUpdateId,
                        String servedReleaseId, List<String> failedIds, String fatalError, String fatalReleaseId) {}

    public void record(Check c) {
        if (c.clientId() == null || c.clientId().isBlank() || c.clientId().length() > 100) return;
        String cid = c.clientId().trim();
        String update = c.currentUpdateId() == null ? null : c.currentUpdateId().trim().toLowerCase(Locale.ROOT);
        String served = c.servedReleaseId() == null ? null : c.servedReleaseId().toLowerCase(Locale.ROOT);
        List<String> failed = c.failedIds() == null ? List.of() : c.failedIds();
        String fatal = c.fatalError() == null || c.fatalError().isBlank() ? null : c.fatalError().trim();
        LocalDate day = LocalDate.now(zone);
        String key = c.appId() + ":" + cid + ":" + update + ":" + c.channel() + ":" + served + ":" + failed + ":" + day;
        long now = System.currentTimeMillis();
        Long prev = lastWrite.get(key);
        if (fatal == null && prev != null && now - prev < THROTTLE_MS) return;
        if (lastWrite.size() > 200_000) lastWrite.clear();
        lastWrite.put(key, now);
        pool.submit(() -> {
            try {
                DeviceSeen d = repo.findByAppIdAndClientId(c.appId(), cid).orElseGet(() -> new DeviceSeen(c.appId(), cid));
                boolean isNew = d.getId() == null;
                boolean firstToday = isNew || d.getLastSeen().isBefore(day.atStartOfDay(zone).toInstant());
                String before = d.getCurrentUpdateId();
                String current = update != null && update.length() <= 64 ? update : null;
                if (current != null && !EMBEDDED.equals(current) && !current.equals(before) && UUID.matcher(current).matches()) {
                    releaseStats.installed(current);
                }
                if (served != null && !served.equals(current) && !served.equals(d.getOfferedUpdateId())) {
                    releaseStats.offered(served);
                    d.setOfferedUpdateId(served);
                }
                Set<String> reported = parseIds(d.getFailedUpdateIds());
                for (String id : failed) if (!reported.contains(id)) releaseStats.failed(id);
                if (!failed.isEmpty() || d.getFailedUpdateIds() != null) d.setFailedUpdateIds(join(failed));
                if (fatal != null) {
                    String pinned = c.fatalReleaseId() != null && UUID.matcher(c.fatalReleaseId()).matches() ? c.fatalReleaseId().toLowerCase(Locale.ROOT) : null;
                    String about = pinned != null ? pinned : !failed.isEmpty() ? failed.get(0) : (current != null && !EMBEDDED.equals(current) ? current : null);
                    launchErrors.record(c.appId(), about, cid, c.platform(), c.runtime(), fatal);
                }
                d.setPlatform(c.platform());
                d.setRuntimeVersion(c.runtime());
                d.setChannel(c.channel());
                d.setCurrentUpdateId(current);
                d.setLastSeen(Instant.now());
                repo.save(d);
                if (firstToday) {
                    DeviceDaily row = daily.findByAppIdAndDay(c.appId(), day).orElseGet(() -> new DeviceDaily(c.appId(), day));
                    row.count(isNew, c.platform());
                    daily.save(row);
                }
            } catch (Exception e) {
                log.debug("记录设备失败：{}", e.toString());
            }
        });
    }

    /** 请求头 Expo-Recent-Failed-Update-IDs 是结构化列表："id1", "id2" */
    public static List<String> parseFailedHeader(String header) {
        if (header == null || header.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        Matcher m = UUID.matcher(header);
        while (m.find() && out.size() < 10) {
            String id = m.group().toLowerCase(Locale.ROOT);
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    private static Set<String> parseIds(String csv) {
        Set<String> s = new HashSet<>();
        if (csv != null) for (String x : csv.split(",")) if (!x.isBlank()) s.add(x.trim());
        return s;
    }

    private static String join(List<String> ids) {
        if (ids.isEmpty()) return null;
        String j = String.join(",", ids);
        return j.length() > 400 ? j.substring(0, 400) : j;
    }

    /** 最近 30 天活跃设备，按当前运行的版本 ID 计数 */
    public Map<String, Long> devicesByUpdate(Long appId) {
        Map<String, Long> m = new HashMap<>();
        for (Object[] row : repo.countByUpdate(appId, Instant.now().minus(ACTIVE_WINDOW))) {
            m.put(row[0] == null ? "" : (String) row[0], (Long) row[1]);
        }
        return m;
    }

    public Map<String, Long> devicesByPlatform(Long appId) {
        Map<String, Long> m = new HashMap<>();
        List<Object[]> rows = repo.countByPlatform(appId, Instant.now().minus(ACTIVE_WINDOW));
        for (Object[] row : rows) m.put(row[0] == null ? "" : (String) row[0], (Long) row[1]);
        return m;
    }

    public long activeDevices(Long appId) {
        return repo.countByAppIdAndLastSeenGreaterThanEqual(appId, Instant.now().minus(ACTIVE_WINDOW));
    }

    public long activeSince(Long appId, Duration window) {
        return repo.countByAppIdAndLastSeenGreaterThanEqual(appId, Instant.now().minus(window));
    }

    public ZoneId zone() {
        return zone;
    }

    /** 一天的设备数：活跃、新设备、iOS、Android */
    public record Day(LocalDate day, long active, long fresh, long ios, long android) {}

    /** 今天直接按设备表算（最准）；以前的按每天记下的数，没有记录的那天是 0 */
    public List<Day> days(Long appId, int count) {
        LocalDate today = LocalDate.now(zone);
        LocalDate from = today.minusDays(count - 1);
        Map<LocalDate, DeviceDaily> rows = new HashMap<>();
        for (DeviceDaily r : daily.findByAppIdAndDayGreaterThanEqualOrderByDayAsc(appId, from)) rows.put(r.getDay(), r);
        List<Day> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            LocalDate d = from.plusDays(i);
            if (d.equals(today)) {
                out.add(today(appId));
                continue;
            }
            DeviceDaily r = rows.get(d);
            out.add(r == null ? new Day(d, 0, 0, 0, 0) : new Day(d, r.getActive(), r.getNewDevices(), r.getIos(), r.getAndroid()));
        }
        return out;
    }

    /** 从哪天开始有按天的记录：升级前的日子没有；一条都没有时就是今天 */
    public LocalDate firstRecord(Long appId) {
        return daily.findFirstByAppIdOrderByDayAsc(appId).map(DeviceDaily::getDay).orElse(LocalDate.now(zone));
    }

    /** 有没有以前的按天记录（刚升级上来的头一天没有，昨天显示「—」） */
    public boolean hasHistory(Long appId, LocalDate day) {
        return daily.findByAppIdAndDay(appId, day).isPresent();
    }

    public Day today(Long appId) {
        LocalDate today = LocalDate.now(zone);
        Instant start = today.atStartOfDay(zone).toInstant();
        long ios = 0, android = 0;
        for (Object[] row : repo.countByPlatform(appId, start)) {
            if ("ios".equalsIgnoreCase(String.valueOf(row[0]))) ios += (Long) row[1];
            else if ("android".equalsIgnoreCase(String.valueOf(row[0]))) android += (Long) row[1];
        }
        return new Day(today, repo.countByAppIdAndLastSeenGreaterThanEqual(appId, start),
                repo.countByAppIdAndFirstSeenGreaterThanEqual(appId, start), ios, android);
    }

    @Override
    public void purge(Long appId) {
        daily.deleteByAppId(appId);
    }

    /** 按天的设备数留 400 天 */
    @Scheduled(cron = "0 23 4 * * *")
    public void prune() {
        daily.deleteOlderThan(LocalDate.now(zone).minusDays(400));
    }

    public Optional<DeviceSeen> device(Long appId, String clientId) {
        if (clientId == null || clientId.isBlank()) return Optional.empty();
        Optional<DeviceSeen> exact = repo.findByAppIdAndClientId(appId, clientId.trim());
        return exact.isPresent() ? exact : repo.findFirstByAppIdAndClientIdIgnoreCase(appId, clientId.trim());
    }
}
