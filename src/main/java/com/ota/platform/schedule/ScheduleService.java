package com.ota.platform.schedule;

import com.ota.platform.app.AppPurge;
import com.ota.platform.app.AppService;
import com.ota.platform.app.Channel;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.ota.OtaEvents;
import com.ota.platform.release.ReleaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 计划任务：每 30 秒看一次有没有到时间的，到了就执行，结果写回任务、记操作日志、照常发通知。
 * 定时调灰度时如果线上版本已经换了，就不执行（免得把新版本直接放量）。
 */
@Service
public class ScheduleService implements AppPurge {

    private static final Logger log = LoggerFactory.getLogger(ScheduleService.class);
    public static final String OPERATOR = "计划任务";

    private final ScheduledTaskRepo repo;
    private final AppService apps;
    private final ReleaseService releases;
    private final AuditService audit;
    private final OtaEvents events;

    public ScheduleService(ScheduledTaskRepo repo, AppService apps, ReleaseService releases, AuditService audit, OtaEvents events) {
        this.repo = repo;
        this.apps = apps;
        this.releases = releases;
        this.audit = audit;
        this.events = events;
    }

    public ScheduledTask scheduleMode(OtaApp app, boolean b, LocalDateTime runAt, String by) {
        return repo.save(ScheduledTask.mode(app.getId(), b, runAt, by));
    }

    public ScheduledTask scheduleRollout(OtaApp app, String channel, String runtime, int percent, LocalDateTime runAt, String by) {
        Channel c = apps.requireChannel(app, channel);
        String current = releases.requireDeployment(c, runtime).getReleaseId();
        if (current == null) throw new IllegalStateException("这个渠道还没有线上版本");
        return repo.save(ScheduledTask.rollout(app.getId(), c.getName(), runtime, current, percent, runAt, by));
    }

    public List<ScheduledTask> pending(Long appId) {
        return repo.findByAppIdAndStatusOrderByRunAtAsc(appId, ScheduledTask.PENDING);
    }

    /** 最近 7 天执行过的（成功或没执行）；取消的不列，只在操作日志里留着 */
    public List<ScheduledTask> recent(Long appId) {
        return repo.findTop5ByAppIdAndStatusInAndDoneAtGreaterThanEqualOrderByDoneAtDesc(appId,
                List.of(ScheduledTask.DONE, ScheduledTask.FAILED), LocalDateTime.now().minusDays(7));
    }

    public Optional<ScheduledTask> find(Long id) {
        return repo.findById(id);
    }

    public ScheduledTask cancel(ScheduledTask t, String by) {
        t.finish(ScheduledTask.CANCELLED, by + " 取消了");
        return repo.save(t);
    }

    @Scheduled(initialDelay = 20_000, fixedDelay = 30_000)
    public void runDue() {
        for (ScheduledTask t : repo.findByStatusAndRunAtLessThanEqualOrderByRunAtAsc(ScheduledTask.PENDING, LocalDateTime.now())) {
            try {
                run(t);
            } catch (Exception e) {
                log.warn("计划任务 {} 执行失败：{}", t.getId(), e.toString());
            }
        }
    }

    /** 执行一个任务（也给「立即执行」用） */
    public ScheduledTask run(ScheduledTask t) {
        OtaApp app = apps.findById(t.getAppId()).orElse(null);
        String plan = "（" + (t.getCreatedBy() == null ? "" : t.getCreatedBy() + " 设置的") + "计划任务）";
        try {
            if (app == null) {
                t.finish(ScheduledTask.FAILED, "App 已经删除了");
            } else if (ScheduledTask.MODE.equals(t.getKind())) {
                boolean b = "B".equals(t.getFace());
                apps.setRedeemOpen(app, b);
                audit.logAs(OPERATOR, app.getId(), "SWITCH", app.getSlug() + " 设为 " + (b ? "B面（上线）" : "A面（审核）") + plan);
                events.modeSwitched(app, b);
                t.finish(ScheduledTask.DONE, "已切换到 " + (b ? "B 面" : "A 面"));
            } else {
                Channel c = apps.requireChannel(app, t.getChannel());
                String current = releases.requireDeployment(c, t.getRuntime()).getReleaseId();
                if (current == null || !current.equals(t.getReleaseId())) {
                    t.finish(ScheduledTask.FAILED, "线上版本已经换了，没有执行（免得把新版本直接放量）");
                } else {
                    ReleaseService.Deployed d = releases.deploy(app, c, current, t.getPercent(), OPERATOR);
                    audit.logAs(OPERATOR, app.getId(), "ACTIVATE", app.getSlug() + " · " + c.getName() + " Runtime " + t.getRuntime()
                            + " 灰度调整为 " + t.getPercent() + "%" + plan);
                    events.deployed(app, d.release(), c.getName(), t.getPercent(), d.republished());
                    t.finish(ScheduledTask.DONE, "灰度已调整到 " + t.getPercent() + "%");
                }
            }
        } catch (Exception e) {
            t.finish(ScheduledTask.FAILED, "没有执行：" + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
        return repo.save(t);
    }

    @Override
    public void purge(Long appId) {
        repo.deleteByAppId(appId);
    }
}
