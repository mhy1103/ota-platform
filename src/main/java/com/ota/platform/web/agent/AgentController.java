package com.ota.platform.web.agent;

import com.ota.platform.web.Csv;
import com.ota.platform.web.AdminUi;
import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.agent.AgentService;
import com.ota.platform.agent.SubAgentService;
import com.ota.platform.audit.AuditService;
import com.ota.platform.codes.ActivationRecord;
import com.ota.platform.common.ApiException;
import com.ota.platform.security.AdminSessions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** 代理登录后看到的页面 */
@Controller
@RequestMapping("/agent")
public class AgentController {

    /** 改密码时当前密码连续输错这么多次，就暂停一会儿 */
    private static final int PASSWORD_TRIES = 5;
    private static final long PASSWORD_LOCK_MS = 15 * 60_000L;

    private final AgentRepo agentRepo;
    private final AgentPortal portal;
    private final AgentService agents;
    private final AdminUi ui;
    private final PasswordEncoder encoder;
    private final AdminSessions sessions;
    private final AuditService audit;
    private final com.ota.platform.notice.NoticeService notices;
    private final com.ota.platform.web.Prefs prefs;
    private final SubAgentService subs;
    private final java.util.Map<Long, long[]> wrongPasswords = new java.util.concurrent.ConcurrentHashMap<>();

    public AgentController(AgentRepo agentRepo, AgentPortal portal, AgentService agents, AdminUi ui,
                           PasswordEncoder encoder, AdminSessions sessions, AuditService audit,
                           com.ota.platform.notice.NoticeService notices, com.ota.platform.web.Prefs prefs, SubAgentService subs) {
        this.subs = subs;
        this.prefs = prefs;
        this.notices = notices;
        this.agentRepo = agentRepo;
        this.portal = portal;
        this.agents = agents;
        this.ui = ui;
        this.encoder = encoder;
        this.sessions = sessions;
        this.audit = audit;
    }

    @GetMapping
    public String overview(@RequestParam(defaultValue = "14") String range, @RequestParam(defaultValue = "") String from,
                           @RequestParam(defaultValue = "") String to, Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        return portal.overview(agent, "/agent", false, range, from, to, model);
    }

    @GetMapping("/records")
    public String records(@RequestParam(defaultValue = "all") String status,
                          @RequestParam(defaultValue = "") String code,
                          @RequestParam(defaultValue = "1") int page,
                          Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        if (!canViewRecords(agent)) return "redirect:/agent";
        return portal.records(agent, "/agent", false, status, code, page, model);
    }

    /** 导出激活记录：和列表一样的筛选，设备号和 IP 脱敏 */
    @GetMapping("/records/export")
    public Object exportRecords(@RequestParam(defaultValue = "all") String status, @RequestParam(defaultValue = "") String code,
                                Authentication auth, HttpServletRequest req) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        if (!canViewRecords(agent)) return "redirect:/agent";
        java.time.LocalDateTime cutoff = portal.cutoff(agent);
        java.util.Map<Long, String> apps = portal.allAppNames();
        Csv csv = new Csv().row("提交时间", "口令", "App", "状态", "确认时间", "设备", "IP");
        java.util.List<ActivationRecord> rows = portal.exportRecords(agent, status, code);
        for (ActivationRecord r : rows) {
            boolean ok = portal.confirmedAsOf(r, cutoff);
            csv.row(ui.full(r.getActivatedAt()), r.getCodeText(), apps.getOrDefault(r.getAppId(), ""), ok ? "已确认" : r.isFast() ? "非正常" : r.isDup() ? "重复" : "待确认",
                    ok && r.getConfirmedAt() != null ? ui.full(r.getConfirmedAt()) : "", ui.maskDevice(r.getDeviceId()), ui.maskIp(r.getDeviceIp()));
        }
        audit.log(null, "AGENT_EXPORT", "代理 " + agent.getName() + "（" + agent.getUsername() + "）导出激活记录 " + rows.size() + " 条");
        return csv.download("激活记录-" + agent.getUsername() + "-" + ui.today() + ".csv");
    }

    @GetMapping("/notices")
    public String notices(Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        return portal.notices(agent, "/agent", false, model);
    }

    @GetMapping("/retention")
    public String retention(@RequestParam(defaultValue = "30") String range, @RequestParam(defaultValue = "") String from,
                            @RequestParam(defaultValue = "") String to, Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        return portal.retention(agent, "/agent", false, range, from, to, model);
    }

    // 开了二级代理的一级代理给自己的二级代理发公告

    @PostMapping("/notices")
    public String noticeCreate(@RequestParam(defaultValue = "") String title, @RequestParam(defaultValue = "") String body,
                               @RequestParam(defaultValue = "false") boolean pinned, @RequestParam(defaultValue = "normal") String level,
                               Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        if (!agent.canManageSubs()) return "redirect:/agent/notices";
        String error = com.ota.platform.notice.NoticeService.validate(title, body);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/agent/notices#sent";
        }
        com.ota.platform.notice.Notice n = notices.createOwn(agent, title, body, pinned, level);
        audit.logAs("代理：" + agent.getName(), null, "SUB_NOTICE_CREATE", "代理 " + agent.getName() + " 给二级代理发公告「" + n.getTitle() + "」");
        ra.addFlashAttribute("msg", "已发布公告「" + n.getTitle() + "」，你的二级代理登录后就能看到");
        return "redirect:/agent/notices#sent";
    }

    @PostMapping("/notices/{id}/update")
    public String noticeUpdate(@PathVariable Long id, @RequestParam(defaultValue = "") String title, @RequestParam(defaultValue = "") String body,
                               @RequestParam(defaultValue = "false") boolean pinned, @RequestParam(defaultValue = "normal") String level,
                               Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        if (!agent.canManageSubs()) return "redirect:/agent/notices";
        String error = com.ota.platform.notice.NoticeService.validate(title, body);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/agent/notices#sent";
        }
        notices.updateOwn(agent, id, title, body, pinned, level).ifPresentOrElse(n -> {
            audit.logAs("代理：" + agent.getName(), null, "SUB_NOTICE_UPDATE", "代理 " + agent.getName() + " 修改给二级代理的公告「" + n.getTitle() + "」");
            ra.addFlashAttribute("msg", "已保存公告「" + n.getTitle() + "」");
        }, () -> ra.addFlashAttribute("error", "公告不存在"));
        return "redirect:/agent/notices#sent";
    }

    @PostMapping("/notices/{id}/pin")
    public String noticePin(@PathVariable Long id, Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        if (!agent.canManageSubs()) return "redirect:/agent/notices";
        notices.pinOwn(agent, id).ifPresent(n -> ra.addFlashAttribute("msg", (n.isPinned() ? "已置顶「" : "已取消置顶「") + n.getTitle() + "」"));
        return "redirect:/agent/notices#sent";
    }

    @PostMapping("/notices/{id}/delete")
    public String noticeDelete(@PathVariable Long id, Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        if (!agent.canManageSubs()) return "redirect:/agent/notices";
        notices.deleteOwn(agent, id).ifPresent(n -> {
            audit.logAs("代理：" + agent.getName(), null, "SUB_NOTICE_DELETE", "代理 " + agent.getName() + " 删除给二级代理的公告「" + n.getTitle() + "」");
            ra.addFlashAttribute("msg", "已删除公告「" + n.getTitle() + "」");
        });
        return "redirect:/agent/notices#sent";
    }

    @PostMapping("/account/prefs")
    @ResponseBody
    public java.util.Map<String, Object> prefs(@RequestParam java.util.Map<String, String> params, Authentication auth) {
        Agent agent = current(auth);
        if (agent != null) prefs.updateAgent(agent.getId(), s -> prefs.merge(s, params));
        return java.util.Map.of("ok", agent != null);
    }

    /** 概览上的公告横幅点「知道了」：记为看过 */
    @PostMapping("/notices/{id}/read")
    @ResponseBody
    public java.util.Map<String, Object> noticeRead(@PathVariable Long id, Authentication auth) {
        Agent agent = current(auth);
        if (agent != null) notices.markRead(agent, java.util.List.of(id));
        return java.util.Map.of("ok", agent != null);
    }

    @GetMapping("/account")
    public String account(Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        return portal.account(agent, "/agent", false, model);
    }

    /** 代理改自己的密码：要输当前密码；改好后其他设备上的登录退出，当前这台不用重新登录 */
    @PostMapping("/account/password")
    public String password(@RequestParam(defaultValue = "") String current, @RequestParam(defaultValue = "") String password,
                           @RequestParam(defaultValue = "") String confirm, Authentication auth, HttpServletRequest req,
                           RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        long now = System.currentTimeMillis();
        long[] wrong = wrongPasswords.get(agent.getId());
        if (wrong != null && wrong[0] >= PASSWORD_TRIES && now - wrong[1] < PASSWORD_LOCK_MS) {
            ra.addFlashAttribute("error", "当前密码输错太多次，15 分钟后再试");
            return "redirect:/agent/account";
        }
        if (!encoder.matches(current, agent.getPasswordHash())) {
            long[] w = wrongPasswords.compute(agent.getId(), (k, v) -> v == null || now - v[1] >= PASSWORD_LOCK_MS ? new long[]{1, now} : new long[]{v[0] + 1, now});
            ra.addFlashAttribute("error", w[0] >= PASSWORD_TRIES ? "当前密码输错太多次，15 分钟后再试" : "当前密码不对");
            return "redirect:/agent/account";
        }
        wrongPasswords.remove(agent.getId());
        String err = password.length() < 8 ? "新密码至少 8 位"
                : password.length() > 64 ? "新密码最多 64 位"
                : !password.equals(confirm) ? "两次输入的新密码不一样"
                : encoder.matches(password, agent.getPasswordHash()) ? "新密码和当前密码一样" : null;
        if (err != null) {
            ra.addFlashAttribute("error", err);
            return "redirect:/agent/account";
        }
        agent.setPasswordHash(encoder.encode(password));
        agentRepo.save(agent);
        int others = sessions.revokeOthersByName(agent.getUsername(), req.getSession().getId());
        audit.log(null, "AGENT_PASSWORD", "代理 " + agent.getName() + "（" + agent.getUsername() + "）修改了自己的密码");
        ra.addFlashAttribute("msg", "密码已修改" + (others > 0 ? "，其他 " + others + " 台设备上的登录已退出" : ""));
        return "redirect:/agent/account";
    }

    @GetMapping("/requests")
    public String requests(Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        if (!agent.canRequest()) return "redirect:/agent";
        return portal.requests(agent, "/agent", false, model);
    }

    /** mode=random 随机生成 count 个；mode=custom 申请 codes 里填的口令（重复的自动拒绝） */
    @PostMapping("/requests")
    public String submit(@RequestParam(defaultValue = "random") String mode,
                         @RequestParam(defaultValue = "0") int count, @RequestParam(defaultValue = "") String codes,
                         @RequestParam(defaultValue = "0") int maxUses, @RequestParam(defaultValue = "") String note,
                         Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        boolean custom = "custom".equals(mode);
        try {
            AgentService.Submitted s = agents.submit(agent, custom, count, codes, maxUses, note, ui.startOfToday());
            String dup = s.rejected().isEmpty() ? "" : s.rejected().size() + " 个重复，已自动拒绝：" + names(s.rejected());
            if (custom && s.request().getCount() == 0) {
                ra.addFlashAttribute("error", "填的 " + s.rejected().size() + " 个口令都重复了，已自动拒绝：" + names(s.rejected()));
            } else if (s.approved() != null) {
                ra.addFlashAttribute("msg", "已生成 " + s.approved().codes().size() + " 个口令，在「数据概览」的我的口令里"
                        + (dup.isEmpty() ? "" : "；" + dup));
            } else {
                ra.addFlashAttribute("msg", (custom ? "已提交 " + s.request().getCount() + " 个口令" : "已提交申请") + "，管理员同意后口令会出现在「数据概览」的我的口令里"
                        + (dup.isEmpty() ? "" : "；" + dup));
            }
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/agent/requests";
    }

    private static String names(java.util.List<com.ota.platform.agent.CodeRequestItem> list) {
        java.util.List<String> codes = list.stream().map(com.ota.platform.agent.CodeRequestItem::getCode).toList();
        return String.join("、", codes.subList(0, Math.min(6, codes.size()))) + (codes.size() > 6 ? " 等" : "");
    }

    @PostMapping("/requests/{id}/cancel")
    public String cancel(@PathVariable Long id, Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        try {
            agents.cancel(agent, id);
            ra.addFlashAttribute("msg", "已撤回申请 #" + id);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/agent/requests";
    }

    // ==================== 二级代理 ====================

    @GetMapping("/subs")
    public String subsPage(@RequestParam(defaultValue = "30") String range, @RequestParam(defaultValue = "") String from,
                           @RequestParam(defaultValue = "") String to, Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        if (!agent.canManageSubs()) return "redirect:/agent";
        return portal.subs(agent, "/agent", false, range, from, to, model);
    }

    @PostMapping("/subs/create")
    public String subCreate(@RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String username,
                            @RequestParam(defaultValue = "") String password, @RequestParam(defaultValue = "false") boolean canViewRecords,
                            @RequestParam(defaultValue = Agent.MODE_REALTIME) String dataMode, @RequestParam(defaultValue = "") String remark,
                            @RequestParam(required = false) String[] tierMin, @RequestParam(required = false) String[] tierMax,
                            @RequestParam(required = false) String[] tierRatio,
                            Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        try {
            Agent s = subs.create(agent, new SubAgentService.Form(name, username, password, canViewRecords, dataMode,
                    com.ota.platform.agent.Deduction.fromForm(tierMin, tierMax, tierRatio), remark));
            ra.addFlashAttribute("msg", "已开二级代理 " + s.getName() + "，登录账号 " + s.getUsername() + "，把账号和密码告诉 TA，在同一个登录页登录");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return subsBack(agent);
    }

    @PostMapping("/subs/{id}/update")
    public String subUpdate(@PathVariable Long id, @RequestParam(defaultValue = "") String name, @RequestParam(defaultValue = "") String username,
                            @RequestParam(defaultValue = "") String password, @RequestParam(defaultValue = "false") boolean canViewRecords,
                            @RequestParam(defaultValue = Agent.MODE_REALTIME) String dataMode, @RequestParam(defaultValue = "") String remark,
                            @RequestParam(required = false) String[] tierMin, @RequestParam(required = false) String[] tierMax,
                            @RequestParam(required = false) String[] tierRatio,
                            Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        try {
            SubAgentService.Saved saved = subs.update(agent, id, new SubAgentService.Form(name, username, password, canViewRecords, dataMode,
                    com.ota.platform.agent.Deduction.fromForm(tierMin, tierMax, tierRatio), remark));
            ra.addFlashAttribute("msg", "已保存二级代理 " + saved.sub().getName() + (password.isEmpty() ? "" : "，密码已重置")
                    + (saved.tiersChanged() ? "；新的扣量从现在起算，之前的激活还按原来的比例，已经扣过的不会变" : ""));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return subsBack(agent);
    }

    @PostMapping("/subs/{id}/toggle")
    public String subToggle(@PathVariable Long id, Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        try {
            Agent s = subs.toggle(agent, id);
            ra.addFlashAttribute("msg", "二级代理 " + s.getName() + (s.getEnabled() ? " 已启用" : " 已停用，TA 不能登录了，分给 TA 的口令照常能用"));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return subsBack(agent);
    }

    @PostMapping("/subs/{id}/refresh")
    public String subRefresh(@PathVariable Long id, Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        try {
            Agent s = subs.refresh(agent, id);
            ra.addFlashAttribute("msg", "已更新二级代理 " + s.getName() + " 的数据，截至 " + ui.dts(s.getSnapshotAt()));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return subsBack(agent);
    }

    @PostMapping("/subs/refresh-all")
    public String subRefreshAll(Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        try {
            int n = subs.refreshAll(agent);
            ra.addFlashAttribute(n > 0 ? "msg" : "error", n > 0 ? "已更新 " + n + " 个手动二级代理的数据" : "没有手动更新的二级代理");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return subsBack(agent);
    }

    @PostMapping("/subs/{id}/delete")
    public String subDelete(@PathVariable Long id, Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        try {
            String name = subs.mine(agent, id).getName();
            int back = subs.delete(agent, id);
            ra.addFlashAttribute("msg", "已删除二级代理 " + name + (back > 0 ? "，TA 的 " + back + " 个口令回到你手里" : ""));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return subsBack(agent);
    }

    /** 分口令：give 是分给 TA 的，take 是从 TA 那里收回的 */
    @PostMapping("/subs/{id}/codes")
    public String subCodes(@PathVariable Long id, @RequestParam(required = false) java.util.List<Long> give,
                           @RequestParam(required = false) java.util.List<Long> take,
                           Authentication auth, HttpServletRequest req, RedirectAttributes ra) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        try {
            SubAgentService.Moved m = subs.assign(agent, id, give, take);
            String name = m.sub().getName();
            ra.addFlashAttribute("msg", m.given() == 0 ? "已从 " + name + " 那里收回 " + m.taken() + " 个口令"
                    : "已给 " + name + " 分了 " + m.given() + " 个口令" + (m.taken() > 0 ? "，收回 " + m.taken() + " 个" : ""));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return subsBack(agent);
    }

    // 以二级代理的视角看：TA 登录后看到的就是这些页面

    @GetMapping("/subs/{id}/preview")
    public String subPreview(@PathVariable Long id, @RequestParam(defaultValue = "14") String range, @RequestParam(defaultValue = "") String from,
                             @RequestParam(defaultValue = "") String to, Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        Agent s = previewable(agent, id);
        return s == null ? "redirect:/agent/subs" : portal.overview(s, "/agent/subs/" + id + "/preview", true, range, from, to, model);
    }

    @GetMapping("/subs/{id}/preview/records")
    public String subPreviewRecords(@PathVariable Long id, @RequestParam(defaultValue = "all") String status,
                                    @RequestParam(defaultValue = "") String code, @RequestParam(defaultValue = "1") int page,
                                    Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        Agent s = previewable(agent, id);
        if (s == null) return "redirect:/agent/subs";
        String base = "/agent/subs/" + id + "/preview";
        return canViewRecords(s) ? portal.records(s, base, true, status, code, page, model) : "redirect:" + base;
    }

    @GetMapping("/subs/{id}/preview/notices")
    public String subPreviewNotices(@PathVariable Long id, Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        Agent s = previewable(agent, id);
        return s == null ? "redirect:/agent/subs" : portal.notices(s, "/agent/subs/" + id + "/preview", true, model);
    }

    @GetMapping("/subs/{id}/preview/retention")
    public String subPreviewRetention(@PathVariable Long id, @RequestParam(defaultValue = "30") String range,
                                      @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to,
                                      Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        Agent s = previewable(agent, id);
        return s == null ? "redirect:/agent/subs" : portal.retention(s, "/agent/subs/" + id + "/preview", true, range, from, to, model);
    }

    @GetMapping("/subs/{id}/preview/account")
    public String subPreviewAccount(@PathVariable Long id, Authentication auth, HttpServletRequest req, Model model) {
        Agent agent = current(auth);
        if (agent == null) return kickOut(req);
        Agent s = previewable(agent, id);
        return s == null ? "redirect:/agent/subs" : portal.account(s, "/agent/subs/" + id + "/preview", true, model);
    }

    /** 改完回二级代理页；没有开二级代理的权限时那个页面会再跳走，提示就丢了，直接回概览 */
    private static String subsBack(Agent agent) {
        return agent.canManageSubs() ? "redirect:/agent/subs" : "redirect:/agent";
    }

    private Agent previewable(Agent agent, Long id) {
        if (!agent.canManageSubs()) return null;
        try {
            return subs.mine(agent, id);
        } catch (ApiException e) {
            return null;
        }
    }

    /** 二级代理看设备记录，上级也得能看 */
    private boolean canViewRecords(Agent a) {
        if (!Boolean.TRUE.equals(a.getCanViewRecords())) return false;
        Agent p = portal.parentOf(a);
        return p == null ? !a.isSub() : Boolean.TRUE.equals(p.getCanViewRecords());
    }

    /** 每次请求都重新读库：管理员停用或删除代理后立即生效，不用等会话过期；二级代理的上级停用、没了权限也一样 */
    private Agent current(Authentication auth) {
        if (auth == null) return null;
        return agentRepo.findByUsername(auth.getName()).filter(subs::active).orElse(null);
    }

    private String kickOut(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return "redirect:/login?disabled";
    }
}
