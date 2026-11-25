package com.ota.platform.codes;

import com.ota.platform.web.AdminUi;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 激活过的设备每天有没有打开 App（日活、留存的数据）：App 启动时校验授权、启动成功确认激活，就记这条激活今天活跃。
 * 在后台写，同一条激活一天只写一次，不拖慢 App 的请求。按天的记录留 400 天
 */
@Service
public class ActiveDays implements com.ota.platform.app.AppPurge {

    private static final Logger log = LoggerFactory.getLogger(ActiveDays.class);
    public static final int KEEP_DAYS = 400;

    private final ActivationDayRepo repo;
    private final ActivationRecordRepo records;
    private final ActivationCodeRepo codes;
    private final com.ota.platform.app.OtaAppRepository apps;
    private final AdminUi ui;
    private final Set<String> done = ConcurrentHashMap.newKeySet();
    /** 已经补过指纹的设备（一台一次就够，和日活每天一次是两回事） */
    private final Set<String> fpReady = ConcurrentHashMap.newKeySet();
    private volatile LocalDate doneDay;
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "active-days");
        t.setDaemon(true);
        return t;
    });

    public ActiveDays(ActivationDayRepo repo, ActivationRecordRepo records, ActivationCodeRepo codes,
                      com.ota.platform.app.OtaAppRepository apps, AdminUi ui) {
        this.repo = repo;
        this.records = records;
        this.codes = codes;
        this.apps = apps;
        this.ui = ui;
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }

    /**
     * App 带着授权来了（校验、确认）：这台设备今天打开过。fp 是算好的设备指纹（可能为空）。
     * 全部放到后台线程做，不拖慢 App 的请求：记日活、更新最近活跃、把指纹补写到算过的那条上（救老用户）。
     */
    public void seen(Long appId, GrantService.Grant g, String fp) {
        if (appId == null || g == null || g.deviceId() == null || g.deviceId().isBlank()) return;
        LocalDate day = ui.today();
        if (!day.equals(doneDay) || done.size() > 500_000) {
            done.clear();
            fpReady.clear();
            doneDay = day;
        }
        String key = appId + "|" + g.code() + "|" + g.deviceId();
        boolean dau = done.add(key);                       // 日活：一天一次
        boolean backfill = fp != null && fpReady.add(key); // 补指纹：一台一次（和日活分开，校验时带了新指纹也能补上）
        if (!dau && !backfill) return;
        pool.submit(() -> {
            try {
                if (!record(appId, g, day, dau, backfill ? fp : null) && dau) done.remove(key);
            } catch (Exception e) {
                if (dau) done.remove(key);
                if (backfill) fpReady.remove(key);
                log.debug("记日活失败：{}", e.toString());
            }
        });
    }

    /** 补指纹（随时）+ 记日活（dau=true 且已确认时）。日活没记上（还没确认）返回 false，下次再记 */
    private boolean record(Long appId, GrantService.Grant g, LocalDate day, boolean dau, String fp) {
        Long codeId = g.code() == null ? null : codes.findByCode(g.code()).map(ActivationCode::getId).orElse(null);
        if (codeId == null) return true;
        ActivationRecord r = records.findByAppIdAndDeviceIdAndCodeId(appId, g.deviceId(), codeId).orElse(null);
        // 别的 App 已经算过的设备：都记在算过的那一条上（跟着 dup_of），同一台设备一天在几个 App 打开也只算一次
        if (r != null && r.getDupOf() != null) r = records.findById(r.getDupOf()).orElse(null);
        if (r == null) return true;
        // 救老用户：把指纹补写到这条（升级前就激活的老记录没有指纹，补上之后别的 App 才认得出）
        if (fp != null && r.getFingerprint() == null) records.setFingerprintIfMissing(r.getId(), fp);
        if (!dau) return true;
        if (!Boolean.TRUE.equals(r.getConfirmed())) return false;
        records.touchLastActive(r.getId(), day);
        if (repo.existsByRecordIdAndDay(r.getId(), day)) return true;
        LocalDateTime at = r.getConfirmedAt() != null ? r.getConfirmedAt() : r.getActivatedAt();
        LocalDate act = at == null ? day : ui.localDate(at);
        int age = (int) Math.max(0, ChronoUnit.DAYS.between(act, day));
        try {
            repo.save(new ActivationDay(r.getId(), r.getAppId(), r.getCodeId(), day, act.isAfter(day) ? day : act, age));
        } catch (DataIntegrityViolationException e) {
            // 同一天别的请求已经记了
        }
        return true;
    }

    @Override
    public void purge(Long appId) {
        repo.deleteByAppId(appId);
    }

    @Scheduled(cron = "0 27 4 * * *")
    public void prune() {
        repo.deleteBefore(ui.today().minusDays(KEEP_DAYS));
        // 每个开了去重、设了保留天数的 App，各自清掉太久没活跃的设备指纹
        for (com.ota.platform.app.OtaApp a : apps.findAll()) {
            int keep = a.getFingerprintKeepDays();
            if (a.isDedupeDevices() && keep > 0) clearStale(a.getSlug(), keep, staleIds(a.getId(), keep));
        }
    }

    /**
     * App 设置里改了指纹保留天数（或刚开去重）：不等凌晨，马上按现在的天数清一遍超出的。
     * 先挑出要清的（只读，很快），返回台数；清在后台按主键一批批做，锁的时间短，不挡着正在激活的请求
     */
    public int pruneNow(com.ota.platform.app.OtaApp a) {
        int keep = a.getFingerprintKeepDays();
        if (!a.isDedupeDevices() || keep <= 0) return 0;
        java.util.List<Long> ids = staleIds(a.getId(), keep);
        if (!ids.isEmpty()) pool.submit(() -> {
            try {
                clearStale(a.getSlug(), keep, ids);
            } catch (Exception e) {
                log.warn("{} 清设备指纹失败：{}", a.getSlug(), e.toString());
            }
        });
        return ids.size();
    }

    private java.util.List<Long> staleIds(Long appId, int keep) {
        LocalDate before = ui.today().minusDays(keep);
        return records.staleFingerprintIds(appId, before, ui.startOf(before));
    }

    private void clearStale(String slug, int keep, java.util.List<Long> ids) {
        LocalDate before = ui.today().minusDays(keep);
        LocalDateTime beforeAt = ui.startOf(before);
        int cleared = 0;
        for (int i = 0; i < ids.size(); i += 500) cleared += records.clearFingerprints(ids.subList(i, Math.min(ids.size(), i + 500)), before, beforeAt);
        if (cleared > 0) log.info("{} 清掉 {} 台超过 {} 天没打开的设备的指纹", slug, cleared, keep);
    }
}
