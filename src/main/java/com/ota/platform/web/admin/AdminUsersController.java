package com.ota.platform.web.admin;

import com.ota.platform.admin.*;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.common.ApiException;
import com.ota.platform.security.AdminSessions;
import com.ota.platform.security.TwoFactorService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/** 管理员：超级管理员新建成员、分配 App 和权限、重置密码、停用、删除 */
@Controller
@RequestMapping("/admin/admins")
public class AdminUsersController {

    private final AdminUserService users;
    private final AccessService access;
    private final AppService apps;
    private final AdminShell shell;
    private final AuditService audit;
    private final TwoFactorService twoFactor;
    private final AdminSessions sessions;

    public AdminUsersController(AdminUserService users, AccessService access, AppService apps, AdminShell shell, AuditService audit,
                                TwoFactorService twoFactor, AdminSessions sessions) {
        this.users = users;
        this.access = access;
        this.apps = apps;
        this.shell = shell;
        this.audit = audit;
        this.twoFactor = twoFactor;
        this.sessions = sessions;
    }

    /** 页面顶上的数字：人数、开了两步验证的、停用的、近 7 天登录过的、从没登录过的 */
    public record Summary(int total, int owners, int members, int tfa, int off, int recent, int never) {}

    /** 列表的一行：管理员和他能看到的 App 及权限 */
    public record Row(AdminUser user, List<Grant> grants) {
        /** 权限相同的 App 合成一行，App 多时列表也只占几行 */
        public List<GrantGroup> groups() {
            Map<String, List<OtaApp>> by = new LinkedHashMap<>();
            for (Grant g : grants) by.computeIfAbsent(g.summary(), k -> new ArrayList<>()).add(g.app());
            List<GrantGroup> out = new ArrayList<>();
            by.forEach((summary, apps) -> out.add(new GrantGroup(summary, apps)));
            return out;
        }

        /** 编辑弹窗用：{"appId": ["release", …]}，写在按钮上，打开弹窗时不用再请求 */
        public String grantsJson() {
            List<String> parts = new ArrayList<>();
            for (Grant g : grants) {
                parts.add("\"" + g.app().getId() + "\":[" + String.join(",", g.perms().stream().map(p -> "\"" + p.getKey() + "\"").toList()) + "]");
            }
            return "{" + String.join(",", parts) + "}";
        }
    }

    /** 列表里显示前 3 个 App 名，其余写成「+N」，鼠标悬停看全部 */
    public record GrantGroup(String summary, List<OtaApp> apps) {
        static final int PREVIEW = 3;

        public String names() {
            return String.join("、", apps.stream().map(OtaApp::getName).toList());
        }

        public String preview() {
            return String.join("、", apps.stream().limit(PREVIEW).map(OtaApp::getName).toList());
        }

        public int more() {
            return Math.max(0, apps.size() - PREVIEW);
        }
    }

    public record Grant(OtaApp app, Set<AppPerm> perms) {
        public String summary() {
            if (perms.isEmpty()) return "只读";
            if (perms.size() == AppPerm.values().length) return "全部权限";
            return String.join("、", perms.stream().map(AppPerm::getLabel).toList());
        }
    }

    @GetMapping
    public String page(Model model, HttpSession session) {
        shell.populate(model, session);
        List<OtaApp> all = apps.all();
        Map<Long, OtaApp> byId = new HashMap<>();
        for (OtaApp a : all) byId.put(a.getId(), a);
        List<Row> rows = new ArrayList<>();
        for (AdminUser u : users.all()) {
            List<Grant> gs = new ArrayList<>();
            if (!u.isOwner()) {
                users.grantsOf(u.getId()).forEach((appId, perms) -> {
                    OtaApp a = byId.get(appId);
                    if (a != null) gs.add(new Grant(a, perms));
                });
                gs.sort(Comparator.comparing(g -> g.app().getCreatedAt()));
            }
            rows.add(new Row(u, gs));
        }
        java.time.Instant weekAgo = java.time.Instant.now().minus(java.time.Duration.ofDays(7));
        int owners = 0, tfa = 0, off = 0, recent = 0, never = 0;
        for (Row r : rows) {
            AdminUser u = r.user();
            if (u.isOwner()) owners++;
            if (u.isTotpEnabled()) tfa++;
            if (!u.isEnabled()) off++;
            if (u.getLastLoginAt() == null) never++;
            else if (u.getLastLoginAt().isAfter(weekAgo)) recent++;
        }
        model.addAttribute("summary", new Summary(rows.size(), owners, rows.size() - owners, tfa, off, recent, never));
        model.addAttribute("rows", rows);
        model.addAttribute("allApps", all);
        model.addAttribute("perms", AppPerm.values());
        model.addAttribute("me", access.current());
        return "admin/admins";
    }

    /** 编辑弹窗要的数据：每个 App 的授权（JSON 写在按钮上） */
    @GetMapping("/{id}/grants")
    @ResponseBody
    public Map<String, Object> grants(@PathVariable Long id) {
        AdminUser u = users.require(id);
        Map<String, Object> out = new LinkedHashMap<>();
        users.grantsOf(u.getId()).forEach((appId, perms) -> out.put(String.valueOf(appId), perms.stream().map(AppPerm::getKey).toList()));
        return out;
    }

    @PostMapping("/create")
    public String create(HttpServletRequest req, RedirectAttributes ra) {
        try {
            AdminUserService.Form f = form(req, true);
            AdminUser u = users.create(f);
            audit.log(null, "ADMIN_CREATE", "新建" + (u.isOwner() ? "超级管理员 " : "成员 ") + u.getName() + "（" + u.getUsername() + "）"
                    + (u.isOwner() ? "" : describe(f)));
            ra.addFlashAttribute("msg", "已新建" + (u.isOwner() ? "超级管理员 " : "成员 ") + u.getName() + "，把账号 " + u.getUsername() + " 和密码告诉 TA 就能登录");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", "没有新建：" + e.getMessage());
        }
        return "redirect:/admin/admins";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id, HttpServletRequest req, RedirectAttributes ra) {
        try {
            AdminUserService.Form f = form(req, false);
            AdminUser u = users.update(id, f, access.current());
            audit.log(null, "ADMIN_UPDATE", "修改管理员 " + u.getName() + "（" + u.getUsername() + "）" + describe(f)
                    + (u.isEnabled() ? "" : "，已停用"));
            ra.addFlashAttribute("msg", "已保存 " + u.getName() + "，权限立即生效");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", "没有保存：" + e.getMessage());
        }
        return "redirect:/admin/admins";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, RedirectAttributes ra) {
        try {
            AdminUser u = users.setEnabled(id, !users.require(id).isEnabled(), access.current());
            if (!u.isEnabled()) sessions.revokeAll(id, u.getUsername());
            audit.log(null, "ADMIN_UPDATE", (u.isEnabled() ? "启用" : "停用") + "管理员 " + u.getName() + "（" + u.getUsername() + "）");
            ra.addFlashAttribute("msg", u.isEnabled() ? "已启用 " + u.getName() + "，TA 可以登录了" : "已停用 " + u.getName() + "，TA 已登录的后台会马上退出");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/admins";
    }

    /** 所有设备都退出登录，密码不变 */
    @PostMapping("/{id}/signout")
    public String signOut(@PathVariable Long id, RedirectAttributes ra) {
        AdminUser u = users.require(id);
        if (u.getId().equals(access.current().id())) {
            ra.addFlashAttribute("error", "自己的登录设备在「我的账号 → 登录与安全」里管理");
            return "redirect:/admin/admins";
        }
        sessions.revokeAll(u.getId(), u.getUsername());
        audit.log(null, "SESSION_REVOKE", "让 " + u.getName() + "（" + u.getUsername() + "）在所有设备上退出登录");
        ra.addFlashAttribute("msg", "已让 " + u.getName() + " 在所有设备上退出登录，TA 用原来的密码重新登录就行");
        return "redirect:/admin/admins";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            AdminUser u = users.delete(id, access.current());
            sessions.revokeAll(id, u.getUsername());
            audit.log(null, "ADMIN_DELETE", "删除管理员 " + u.getName() + "（" + u.getUsername() + "）");
            ra.addFlashAttribute("msg", "已删除 " + u.getName() + "，TA 已登录的后台会立即退出");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/admins";
    }

    /** 成员手机丢了、换了：超级管理员重置 TA 的两步验证，TA 用密码登录后再重新开启 */
    @PostMapping("/{id}/2fa/reset")
    public String resetTwoFactor(@PathVariable Long id, RedirectAttributes ra) {
        AdminUser u = users.require(id);
        if (!u.isTotpEnabled()) {
            ra.addFlashAttribute("error", u.getName() + " 没有开启两步验证");
            return "redirect:/admin/admins";
        }
        twoFactor.disable(id);
        audit.log(null, "TOTP_RESET", "重置 " + u.getName() + "（" + u.getUsername() + "）的两步验证");
        ra.addFlashAttribute("msg", "已重置 " + u.getName() + " 的两步验证，TA 下次只用密码就能登录，建议登录后重新开启");
        return "redirect:/admin/admins";
    }

    /** 从表单读出管理员和授权：access_{appId}=on 表示能看这个 App，perm_{appId}=release&perm_{appId}=mode…；manageCodes、manageAgents 不分 App */
    private AdminUserService.Form form(HttpServletRequest req, boolean creating) {
        boolean owner = "true".equals(req.getParameter("owner"));
        Map<Long, Set<AppPerm>> grants = new LinkedHashMap<>();
        if (!owner) {
            for (OtaApp a : apps.all()) {
                String id = String.valueOf(a.getId());
                String[] perms = req.getParameterValues("perm_" + id);
                boolean on = req.getParameter("access_" + id) != null || (perms != null && perms.length > 0);
                if (!on) continue;
                Set<AppPerm> set = EnumSet.noneOf(AppPerm.class);
                if (perms != null) for (String p : perms) {
                    AppPerm ap = AppPerm.of(p);
                    if (ap != null) set.add(ap);
                }
                grants.put(a.getId(), set);
            }
        }
        String password = req.getParameter("password");
        if (creating && (password == null || password.isEmpty())) throw ApiException.badRequest("BAD_PASSWORD", "请设置登录密码，至少 8 位");
        return new AdminUserService.Form(req.getParameter("username"), req.getParameter("name"), password, owner,
                "true".equals(req.getParameter("manageAgents")), "true".equals(req.getParameter("manageCodes")), !"false".equals(req.getParameter("enabled")),
                req.getParameter("remark"), grants, "true".equals(req.getParameter("forceChange")));
    }

    private String describe(AdminUserService.Form f) {
        if (f.owner()) return "：超级管理员";
        String extra = (f.manageCodes() ? "，可以管理口令" : "") + (f.manageAgents() ? "，可以管理代理" : "");
        if (f.grants().isEmpty()) return "：还没有分配 App" + extra;
        List<String> parts = new ArrayList<>();
        f.grants().forEach((appId, perms) -> apps.findById(appId).ifPresent(a -> parts.add(a.getSlug() + "（"
                + (perms.isEmpty() ? "只读" : String.join("、", perms.stream().map(AppPerm::getLabel).toList())) + "）")));
        return "：" + String.join("，", parts) + extra;
    }
}
