package com.ota.platform.web.admin;

import com.ota.platform.web.AdminUi;
import com.ota.platform.admin.*;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.common.ApiException;
import com.ota.platform.security.AdminSessions;
import com.ota.platform.security.LoginHistory;
import com.ota.platform.security.TwoFactorService;
import com.ota.platform.settings.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.*;

/** 我的账号：每个管理员改自己的名字、账号、密码，看自己有哪些权限 */
@Controller
@RequestMapping("/admin/account")
public class AccountController {

    private final AdminUserService users;
    private final AccessService access;
    private final AdminShell shell;
    private final AuditService audit;
    private final TwoFactorService twoFactor;
    private final AdminSessions sessions;
    private final LoginHistory history;
    private final SettingService settings;
    private final com.ota.platform.config.OtaProperties props;
    private final AdminUi ui;
    private final com.ota.platform.security.AdminLogins logins;
    private final com.ota.platform.web.Prefs prefs;

    private static final String SETUP = "ota.2fa.setup";

    public AccountController(AdminUserService users, AccessService access, AdminShell shell, AuditService audit,
                             TwoFactorService twoFactor, AdminSessions sessions, LoginHistory history, SettingService settings,
                             com.ota.platform.config.OtaProperties props, AdminUi ui, com.ota.platform.security.AdminLogins logins,
                             com.ota.platform.web.Prefs prefs) {
        this.logins = logins;
        this.prefs = prefs;
        this.props = props;
        this.ui = ui;
        this.users = users;
        this.access = access;
        this.shell = shell;
        this.audit = audit;
        this.twoFactor = twoFactor;
        this.sessions = sessions;
        this.history = history;
        this.settings = settings;
    }

    public record MyGrant(OtaApp app, Set<AppPerm> perms) {}

    @GetMapping
    public String page(Model model, HttpSession session) {
        shell.populate(model, session);
        CurrentAdmin me = access.current();
        AdminUser user = users.require(me.id());
        List<MyGrant> grants = new ArrayList<>();
        for (OtaApp a : shell.visible()) {
            grants.add(new MyGrant(a, me.owner() ? EnumSet.allOf(AppPerm.class) : me.grants().getOrDefault(a.getId(), Set.of())));
        }
        model.addAttribute("user", user);
        model.addAttribute("me", me);
        model.addAttribute("myGrants", grants);
        model.addAttribute("perms", AppPerm.values());
        model.addAttribute("recoveryLeft", twoFactor.recoveryLeft(user));
        model.addAttribute("sessions", sessions.of(me.id(), me.username(), session.getId()));
        model.addAttribute("logins", history.recent(me.id()));
        model.addAttribute("maxDevices", logins.maxDevices());
        model.addAttribute("require2fa", settings.require2fa());
        return "admin/account";
    }

    @PostMapping
    public String save(@RequestParam(defaultValue = "") String name,
                       @RequestParam(defaultValue = "") String username,
                       @RequestParam(defaultValue = "") String currentPassword,
                       @RequestParam(defaultValue = "") String newPassword,
                       @RequestParam(defaultValue = "") String confirmPassword,
                       HttpServletRequest req, RedirectAttributes ra) {
        CurrentAdmin me = access.current();
        if (!newPassword.equals(confirmPassword)) {
            ra.addFlashAttribute("error", "两次输入的新密码不一致");
            return "redirect:/admin/account";
        }
        boolean relogin;
        try {
            relogin = users.updateSelf(me.id(), name, username, currentPassword, newPassword);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", "未保存：" + e.getMessage());
            return "redirect:/admin/account";
        }
        audit.log(null, "ACCOUNT_UPDATE", me.username() + " 修改自己的账号" + (newPassword.isEmpty() ? "" : "和密码"));
        if (!relogin) {
            ra.addFlashAttribute("msg", "已保存");
            return "redirect:/admin/account";
        }
        HttpSession session = req.getSession(false);
        if (session != null) session.invalidate();
        SecurityContextHolder.clearContext();
        return "redirect:/login?changed";
    }

    /** 页面引导看完或跳过时调用，下次不再自动弹出 */
    @PostMapping("/guide")
    @ResponseBody
    public Map<String, Object> guideSeen(@RequestParam(defaultValue = "") String key) {
        try {
            users.markGuide(access.current().id(), key);
            return Map.of("ok", true);
        } catch (ApiException e) {
            return Map.of("ok", false, "msg", e.getMessage());
        }
    }

    @PostMapping("/prefs")
    @ResponseBody
    public Map<String, Object> prefs(@RequestParam Map<String, String> params) {
        prefs.updateAdmin(access.current().id(), s -> prefs.merge(s, params));
        return Map.of("ok", true);
    }

    /** 概览看板：cards 是显示的卡片，按顺序，可以带宽度和占几行（trend:6、ops:6r2）；reset 恢复默认。回给页面新的布局指纹 */
    @PostMapping("/board")
    @ResponseBody
    public Map<String, Object> board(@RequestParam(defaultValue = "") String cards, @RequestParam(defaultValue = "false") boolean reset) {
        List<String> list = reset ? null : Arrays.stream(cards.split(",")).map(OverviewBoard::normalize).filter(java.util.Objects::nonNull).toList();
        Long me = access.current().id();
        prefs.updateAdmin(me, s -> prefs.withBoard(s, list));
        return Map.of("ok", true, "sig", prefs.boardSigOf(me));
    }

    /** 看板上便签、今日目标的内容 */
    @PostMapping("/board/data")
    @ResponseBody
    public Map<String, Object> boardData(@RequestParam String key, @RequestParam(defaultValue = "") String value) {
        prefs.updateAdmin(access.current().id(), s -> prefs.withCardData(s, key, value));
        return Map.of("ok", true);
    }

    @PostMapping("/guide/reset")
    public String guideReset(RedirectAttributes ra) {
        users.resetGuide(access.current().id());
        ra.addFlashAttribute("msg", "引导已重置，现在会重新显示后台导览");
        return "redirect:/admin";
    }

    // ---------- 两步验证 ----------

    /** 生成新密钥和二维码，先放在会话里，输对一次动态码才真正开启 */
    @PostMapping("/2fa/setup")
    @ResponseBody
    public Map<String, Object> twoFactorSetup(HttpServletRequest req) {
        CurrentAdmin me = access.current();
        if (users.require(me.id()).isTotpEnabled()) return Map.of("ok", false, "msg", "已经开启了两步验证");
        HttpSession session = req.getSession();
        TwoFactorService.Setup s = twoFactor.begin(me.username(), props.baseUrl().isEmpty() ? req.getServerName() : ui.host(props.baseUrl()), (String) session.getAttribute(SETUP));
        session.setAttribute(SETUP, s.secret());
        return Map.of("ok", true, "secret", s.grouped(), "uri", s.uri(), "qr", s.qr());
    }

    @PostMapping("/2fa/enable")
    public String twoFactorEnable(@RequestParam(defaultValue = "") String code, HttpSession session, RedirectAttributes ra) {
        CurrentAdmin me = access.current();
        try {
            List<String> codes = twoFactor.enable(me.id(), (String) session.getAttribute(SETUP), code);
            session.removeAttribute(SETUP);
            audit.log(null, "TOTP_ENABLE", me.username() + " 开启两步验证");
            ra.addFlashAttribute("msg", "两步验证已开启，以后登录要输入动态码");
            ra.addFlashAttribute("recoveryCodes", codes);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("totpRetry", true);
        }
        return "redirect:/admin/account#security";
    }

    @PostMapping("/2fa/disable")
    public String twoFactorDisable(@RequestParam(defaultValue = "") String password, @RequestParam(defaultValue = "") String code,
                                   RedirectAttributes ra) {
        CurrentAdmin me = access.current();
        AdminUser u = users.require(me.id());
        if (!users.passwordMatches(me.id(), password)) {
            ra.addFlashAttribute("error", "密码不正确，两步验证没有关闭");
        } else if (!twoFactor.verify(u, code) && !twoFactor.useRecovery(me.id(), code)) {
            ra.addFlashAttribute("error", "动态码或恢复码不对，两步验证没有关闭");
        } else {
            twoFactor.disable(me.id());
            audit.log(null, "TOTP_DISABLE", me.username() + " 关闭两步验证");
            ra.addFlashAttribute("msg", "两步验证已关闭");
        }
        return "redirect:/admin/account#security";
    }

    @PostMapping("/2fa/recovery")
    public String twoFactorRecovery(@RequestParam(defaultValue = "") String password, RedirectAttributes ra) {
        CurrentAdmin me = access.current();
        if (!users.passwordMatches(me.id(), password)) {
            ra.addFlashAttribute("error", "密码不正确，恢复码没有重新生成");
            return "redirect:/admin/account#security";
        }
        try {
            ra.addFlashAttribute("recoveryCodes", twoFactor.regenerate(me.id()));
            audit.log(null, "TOTP_RECOVERY", me.username() + " 重新生成恢复码");
            ra.addFlashAttribute("msg", "已生成新的恢复码，旧的全部作废");
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/account#security";
    }

    // ---------- 登录设备 ----------

    @PostMapping("/sessions/revoke")
    public String revoke(@RequestParam(defaultValue = "") String id, HttpSession session, RedirectAttributes ra) {
        CurrentAdmin me = access.current();
        if (sessions.revoke(me.id(), me.username(), id, session.getId())) {
            audit.log(null, "SESSION_REVOKE", me.username() + " 退出了一台登录设备");
            ra.addFlashAttribute("msg", "已退出这台设备");
        } else {
            ra.addFlashAttribute("error", "这台设备已经不在登录状态");
        }
        return "redirect:/admin/account#security";
    }

    @PostMapping("/sessions/revoke-others")
    public String revokeOthers(HttpSession session, RedirectAttributes ra) {
        CurrentAdmin me = access.current();
        int n = sessions.revokeOthers(me.id(), me.username(), session.getId());
        if (n > 0) audit.log(null, "SESSION_REVOKE", me.username() + " 退出了其他 " + n + " 台登录设备");
        ra.addFlashAttribute("msg", n > 0 ? "已退出其他 " + n + " 台设备" : "没有其他登录着的设备");
        return "redirect:/admin/account#security";
    }
}
