package com.ota.platform.web.admin;

import com.ota.platform.web.AdminUi;
import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AppPerm;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.schedule.ScheduleService;
import com.ota.platform.schedule.ScheduledTask;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;

/** 计划任务：定时切换 A/B 面（运行模式权限）、定时调整灰度（发布版本权限） */
@Controller
@RequestMapping("/admin/schedules")
public class AdminScheduleController {

    private final ScheduleService schedules;
    private final AdminShell shell;
    private final AdminUi ui;
    private final AuditService audit;
    private final AccessService access;

    public AdminScheduleController(ScheduleService schedules, AdminShell shell, AdminUi ui, AuditService audit, AccessService access) {
        this.schedules = schedules;
        this.shell = shell;
        this.ui = ui;
        this.audit = audit;
        this.access = access;
    }

    @PostMapping("/mode")
    public String mode(@RequestParam String face, @RequestParam String runAt, HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        LocalDateTime at = parse(runAt, ra);
        if (at == null) return "redirect:/admin";
        boolean b = "B".equals(face);
        ScheduledTask t = schedules.scheduleMode(app, b, at, AuditService.operator());
        audit.log(app.getId(), "SCHEDULE", app.getSlug() + " 计划在 " + ui.full(at) + " " + t.getWhat());
        ra.addFlashAttribute("msg", "已设好：" + ui.dt(at) + " " + t.getWhat() + "，到时间自动执行");
        return "redirect:/admin";
    }

    @PostMapping("/rollout")
    public String rollout(@RequestParam String channel, @RequestParam String runtime, @RequestParam int percent,
                          @RequestParam String runAt, HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        LocalDateTime at = parse(runAt, ra);
        if (at == null) return "redirect:/admin/releases";
        if (percent < 1 || percent > 100) {
            ra.addFlashAttribute("error", "灰度要在 1 到 100 之间");
            return "redirect:/admin/releases";
        }
        try {
            ScheduledTask t = schedules.scheduleRollout(app, channel, runtime, percent, at, AuditService.operator());
            audit.log(app.getId(), "SCHEDULE", app.getSlug() + " 计划在 " + ui.full(at) + " 把 " + t.getWhat());
            ra.addFlashAttribute("msg", "已设好：" + ui.dt(at) + " 把 " + t.getWhat() + "，到时间自动执行");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "没有设好：" + (e.getMessage() == null ? e.toString() : e.getMessage()));
        }
        return "redirect:/admin/releases";
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, @RequestParam(defaultValue = "/admin") String back, RedirectAttributes ra) {
        String to = "/admin/releases".equals(back) ? back : "/admin";
        ScheduledTask t = schedules.find(id).orElse(null);
        CurrentAdmin me = access.current();
        if (t == null || !t.isPending()) {
            ra.addFlashAttribute("error", "这个计划已经执行过或者取消了");
            return "redirect:" + to;
        }
        AppPerm need = ScheduledTask.MODE.equals(t.getKind()) ? AppPerm.MODE : AppPerm.RELEASE;
        if (me == null || !me.can(t.getAppId(), need)) {
            ra.addFlashAttribute("error", "没有权限取消这个计划");
            return "redirect:" + to;
        }
        schedules.cancel(t, AuditService.operator());
        audit.log(t.getAppId(), "SCHEDULE", "取消计划：" + ui.full(t.getRunAt()) + " " + t.getWhat());
        ra.addFlashAttribute("msg", "已取消：" + ui.dt(t.getRunAt()) + " " + t.getWhat());
        return "redirect:" + to;
    }

    /** 页面上的时间（后台时区，datetime-local 格式）转成服务器时间；要在 1 分钟后、90 天内 */
    private LocalDateTime parse(String runAt, RedirectAttributes ra) {
        try {
            LocalDateTime local = LocalDateTime.parse(runAt.trim().length() == 16 ? runAt.trim() + ":00" : runAt.trim());
            LocalDateTime at = local.atZone(ZoneId.of(ui.zoneId())).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
            LocalDateTime now = LocalDateTime.now();
            if (at.isBefore(now.plusMinutes(1))) {
                ra.addFlashAttribute("error", "执行时间要晚于现在");
                return null;
            }
            if (at.isAfter(now.plusDays(90))) {
                ra.addFlashAttribute("error", "最多提前 90 天设置");
                return null;
            }
            return at;
        } catch (DateTimeParseException | NullPointerException e) {
            ra.addFlashAttribute("error", "执行时间格式不对");
            return null;
        }
    }
}
