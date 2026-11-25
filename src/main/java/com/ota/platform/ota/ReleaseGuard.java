package com.ota.platform.ota;

import com.ota.platform.app.AppPurge;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.settings.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * 启动失败自动回滚：线上版本启动失败的台数和失败率都到了设定值，就回滚到上一版本（和手动回滚一样，旧版本复制成新版本发布）。
 * 每个 App 单独开，默认不开。每个版本只自动回滚一次，回滚后有人又手动发布它就不再管；
 * 渠道没有上一版本时不会自动回退到安装包内置版本，照常只告警。
 */
@Service
public class ReleaseGuard implements AppPurge {

    private static final Logger log = LoggerFactory.getLogger(ReleaseGuard.class);
    public static final String OPERATOR = "自动回滚";
    public static final List<Integer> RATES = List.of(5, 10, 20, 30);
    public static final List<Integer> MINIMUMS = List.of(3, 5, 10, 20, 50);
    /** 值是「on 或 off:失败率:台数」 */
    private static final String KEY = "guard.";

    public record Config(boolean on, int rate, int min) {
        public static final Config OFF = new Config(false, 10, 5);

        public boolean getOn() { return on; }
        public int getRate() { return rate; }
        public int getMin() { return min; }
    }

    private final SettingService settings;
    private final ReleaseService releases;
    private final AppService apps;
    private final AuditService audit;
    private final OtaEvents events;
    private final ReleaseStatRepo stats;

    public ReleaseGuard(SettingService settings, ReleaseService releases, AppService apps, AuditService audit, OtaEvents events,
                        ReleaseStatRepo stats) {
        this.settings = settings;
        this.releases = releases;
        this.apps = apps;
        this.audit = audit;
        this.events = events;
        this.stats = stats;
    }

    public Config config(Long appId) {
        String[] p = settings.get(KEY + appId, "").split(":");
        if (p.length != 3) return Config.OFF;
        try {
            int rate = Integer.parseInt(p[1]), min = Integer.parseInt(p[2]);
            return new Config("on".equals(p[0]), RATES.contains(rate) ? rate : Config.OFF.rate(), MINIMUMS.contains(min) ? min : Config.OFF.min());
        } catch (NumberFormatException e) {
            return Config.OFF;
        }
    }

    public Config save(Long appId, boolean on, int rate, int min) {
        Config c = new Config(on, RATES.contains(rate) ? rate : Config.OFF.rate(), MINIMUMS.contains(min) ? min : Config.OFF.min());
        settings.set(KEY + appId, (c.on() ? "on" : "off") + ":" + c.rate() + ":" + c.min());
        return c;
    }

    /** 版本统计写库以后调用，s 是刚报了新的启动失败的版本；回滚了返回 true */
    public boolean check(ReleaseStat s) {
        if (s.getRolledBackAt() != null) return false;
        Release r = releases.find(s.getReleaseId()).orElse(null);
        if (r == null) return false;
        Config c = config(r.getAppId());
        if (!c.on() || s.getFailed() < c.min() || s.getFailRate() < c.rate()) return false;
        // 手动再发布自动回滚掉的版本时会复制成新版本，按原版本算，不跟人对着干
        if (rolledBack(ReleaseService.origin(r))) return false;
        OtaApp app = apps.findById(r.getAppId()).orElse(null);
        List<ReleaseService.LiveSpot> spots = releases.liveSpots(r.getId());
        if (app == null || spots.isEmpty()) return false;
        s.setRolledBackAt(Instant.now());
        stats.save(s);
        String why = "启动失败 " + s.getFailed() + " 台（" + s.getFailRate() + "%），达到设定的 " + c.rate() + "% 且 " + c.min() + " 台";
        boolean done = false;
        for (ReleaseService.LiveSpot spot : spots) {
            String channel = spot.channel().getName();
            String prev = spot.deployment().getPreviousReleaseId();
            Release prevRelease = prev == null ? null : releases.find(prev).orElse(null);
            if (prevRelease != null && rolledBack(ReleaseService.origin(prevRelease))) {
                audit.logAs(OPERATOR, app.getId(), "ROLLBACK", app.getSlug() + " · " + channel + " 的 " + r.shortId() + " " + why
                        + "，但上一版本 " + prevRelease.shortId() + " 是之前自动回滚掉的，没有回滚过去");
                events.autoRollbackSkipped(app, r, channel, s, "上一版本 " + prevRelease.shortId() + " 是之前自动回滚掉的");
                continue;
            }
            if (prevRelease == null) {
                audit.logAs(OPERATOR, app.getId(), "ROLLBACK", app.getSlug() + " · " + channel + " 的 " + r.shortId() + " " + why
                        + "，但没有上一版本，没有自动回滚");
                events.autoRollbackSkipped(app, r, channel, s, "这个渠道没有上一版本");
                continue;
            }
            try {
                ReleaseService.Deployed d = releases.rollback(app, spot.channel(), r.getRuntimeVersion(), null, OPERATOR);
                Release to = d.release();
                String toId = to.getRepublishedFrom() != null ? to.getRepublishedFrom().substring(0, 8) : to.shortId();
                audit.logAs(OPERATOR, app.getId(), "ROLLBACK", app.getSlug() + " · " + channel + " 的 " + r.shortId() + " " + why
                        + "，自动回滚到 " + toId + "（新版本 " + to.shortId() + "，Runtime " + r.getRuntimeVersion() + "）");
                events.autoRolledBack(app, r, to, channel, s, c.rate(), c.min());
                done = true;
            } catch (Exception e) {
                log.warn("自动回滚 {} {} 失败：{}", app.getSlug(), channel, e.toString());
                audit.logAs(OPERATOR, app.getId(), "ROLLBACK", app.getSlug() + " · " + channel + " 的 " + r.shortId() + " " + why
                        + "，自动回滚没有成功：" + (e.getMessage() == null ? e.toString() : e.getMessage()));
            }
        }
        return done;
    }

    private boolean rolledBack(String releaseId) {
        return stats.findById(releaseId).map(x -> x.getRolledBackAt() != null).orElse(false);
    }

    @Override
    public void purge(Long appId) {
        settings.set(KEY + appId, "");
    }
}
