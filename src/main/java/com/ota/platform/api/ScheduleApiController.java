package com.ota.platform.api;

import com.ota.platform.audit.AppContext;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.auth.TokenPrincipal;
import com.ota.platform.common.ApiException;
import com.ota.platform.schedule.ScheduleService;
import com.ota.platform.schedule.ScheduledTask;
import com.ota.platform.web.AdminUi;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** 管理接口：计划任务（定时切换 A/B 面、定时调整灰度），给 CI 和运维脚本用 */
@RestController
@RequestMapping("/api/v1")
public class ScheduleApiController {

    private final AppService apps;
    private final ScheduleService schedules;
    private final AuditService audit;
    private final AdminUi ui;

    public ScheduleApiController(AppService apps, ScheduleService schedules, AuditService audit, AdminUi ui) {
        this.apps = apps;
        this.schedules = schedules;
        this.audit = audit;
        this.ui = ui;
    }

    // ==================== 计划任务 ====================

    /** type：mode（切换 A/B 面，face 为 A 或 B）或 rollout（调整灰度，channel、runtimeVersion、percent） */
    public record ScheduleRequest(String type, String runAt, String face, String channel, String runtimeVersion, Integer percent) {}

    @GetMapping("/apps/{slug}/schedules")
    public Map<String, Object> list(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug) {
        OtaApp app = app(slug);
        p.requireApp(app.getId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", schedules.pending(app.getId()).stream().map(this::view).toList());
        m.put("recent", schedules.recent(app.getId()).stream().map(this::view).toList());
        return m;
    }

    @PostMapping("/apps/{slug}/schedules")
    public Map<String, Object> create(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @RequestBody ScheduleRequest req) {
        OtaApp app = app(slug);
        String type = req.type() == null ? "" : req.type().trim().toLowerCase(Locale.ROOT);
        if ("mode".equals(type)) p.requireAdmin();
        else if ("rollout".equals(type)) p.requireApp(app.getId());
        else throw ApiException.badRequest("BAD_TYPE", "type 要是 mode 或 rollout");
        LocalDateTime at = runAt(req.runAt());
        String by = "令牌 " + p.name();
        ScheduledTask t;
        if ("mode".equals(type)) {
            String face = req.face() == null ? "" : req.face().trim().toUpperCase(Locale.ROOT);
            if (!"A".equals(face) && !"B".equals(face)) throw ApiException.badRequest("BAD_FACE", "face 要是 A 或 B");
            t = schedules.scheduleMode(app, "B".equals(face), at, by);
        } else {
            if (req.percent() == null || req.percent() < 1 || req.percent() > 100) {
                throw ApiException.badRequest("BAD_ROLLOUT", "percent 要在 1 到 100 之间");
            }
            if (blank(req.channel()) || blank(req.runtimeVersion())) throw ApiException.badRequest("BAD_REQUEST", "要填 channel 和 runtimeVersion");
            try {
                t = schedules.scheduleRollout(app, req.channel().trim(), req.runtimeVersion().trim(), req.percent(), at, by);
            } catch (IllegalStateException e) {
                throw ApiException.badRequest("NO_DEPLOYMENT", e.getMessage());
            }
        }
        audit.log(app.getId(), "SCHEDULE", app.getSlug() + " 计划在 " + ui.full(at) + (ScheduledTask.ROLLOUT.equals(t.getKind()) ? " 把 " : " ")
                + t.getWhat() + " · " + by);
        return view(t);
    }

    @DeleteMapping("/apps/{slug}/schedules/{id}")
    public Map<String, Object> cancel(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug, @PathVariable Long id) {
        OtaApp app = app(slug);
        ScheduledTask t = schedules.find(id).filter(x -> x.getAppId().equals(app.getId()))
                .orElseThrow(() -> ApiException.notFound("SCHEDULE_NOT_FOUND", "没有这个计划"));
        if (ScheduledTask.MODE.equals(t.getKind())) p.requireAdmin();
        else p.requireApp(app.getId());
        if (!t.isPending()) throw ApiException.conflict("NOT_PENDING", "这个计划已经执行过或者取消了");
        schedules.cancel(t, "令牌 " + p.name());
        audit.log(app.getId(), "SCHEDULE", "取消计划：" + ui.full(t.getRunAt()) + " " + t.getWhat() + " · 令牌 " + p.name());
        return view(t);
    }

    /** 带时区的时间直接用；不带时区的按后台时区算。要在 1 分钟后、90 天内 */
    private LocalDateTime runAt(String text) {
        if (blank(text)) throw ApiException.badRequest("BAD_TIME", "要填 runAt，比如 2026-11-13T10:00:00+08:00");
        Instant when;
        try {
            when = OffsetDateTime.parse(text.trim()).toInstant();
        } catch (DateTimeParseException e) {
            try {
                when = LocalDateTime.parse(text.trim()).atZone(ZoneId.of(ui.zoneId())).toInstant();
            } catch (DateTimeParseException e2) {
                throw ApiException.badRequest("BAD_TIME", "runAt 格式不对，用 ISO 8601，比如 2026-11-13T10:00:00+08:00");
            }
        }
        Instant now = Instant.now();
        if (when.isBefore(now.plus(Duration.ofMinutes(1)))) throw ApiException.badRequest("BAD_TIME", "执行时间要晚于现在至少 1 分钟");
        if (when.isAfter(now.plus(Duration.ofDays(90)))) throw ApiException.badRequest("BAD_TIME", "最多提前 90 天设置");
        return LocalDateTime.ofInstant(when, ZoneId.systemDefault());
    }

    private Map<String, Object> view(ScheduledTask t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("type", ScheduledTask.MODE.equals(t.getKind()) ? "mode" : "rollout");
        m.put("what", t.getWhat());
        if (ScheduledTask.MODE.equals(t.getKind())) {
            m.put("face", t.getFace());
        } else {
            m.put("channel", t.getChannel());
            m.put("runtimeVersion", t.getRuntime());
            m.put("percent", t.getPercent());
            m.put("releaseId", t.getReleaseId());
        }
        m.put("runAt", iso(t.getRunAt()));
        m.put("status", t.getStatus().toLowerCase(Locale.ROOT));
        m.put("result", t.getResult());
        m.put("createdBy", t.getCreatedBy());
        m.put("createdAt", iso(t.getCreatedAt()));
        m.put("doneAt", iso(t.getDoneAt()));
        return m;
    }

    private String iso(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).withZoneSameInstant(ZoneId.of(ui.zoneId())).toOffsetDateTime().toString();
    }

    private OtaApp app(String slug) {
        OtaApp a = apps.require(slug);
        AppContext.set(a);
        return a;
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
