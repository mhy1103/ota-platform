package com.ota.platform.security;

import com.ota.platform.admin.AdminUser;
import com.ota.platform.admin.AdminUserRepo;
import com.ota.platform.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;

/** 密码对了、开了两步验证的管理员，在这里输入动态码或恢复码才算登录完成 */
@Controller
public class TwoFactorLoginController {

    public static final String PENDING = "ota.2fa.pending";
    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_TRIES = 5;

    /** 会话存在数据库里，改了要重新 setAttribute 才会保存，所以每次失败换一个新对象 */
    public record Pending(Long adminId, String username, Instant at, int fails, boolean remember) implements Serializable {
        Pending failed() {
            return new Pending(adminId, username, at, fails + 1, remember);
        }
    }

    private final AdminUserRepo users;
    private final TwoFactorService twoFactor;
    private final UserDetailsService userDetails;
    private final AdminLogins logins;
    private final LoginHistory history;
    private final AuditService audit;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public TwoFactorLoginController(AdminUserRepo users, TwoFactorService twoFactor, UserDetailsService userDetails,
                                    AdminLogins logins, LoginHistory history, AuditService audit) {
        this.users = users;
        this.twoFactor = twoFactor;
        this.userDetails = userDetails;
        this.logins = logins;
        this.history = history;
        this.audit = audit;
    }

    /** 密码登录成功后调用：先不算登录，记下是谁，去输动态码 */
    public static void hold(HttpServletRequest req, AdminUser u) {
        HttpSession s = req.getSession();
        s.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        SecurityContextHolder.clearContext();
        s.setAttribute(PENDING, new Pending(u.getId(), u.getUsername(), Instant.now(), 0, LoginLifetime.wanted(req)));
    }

    @GetMapping("/login/2fa")
    public String page(HttpSession session, Model model) {
        Pending p = pending(session);
        if (p == null) return "redirect:/login?expired2fa";
        model.addAttribute("username", p.username());
        return "login2fa";
    }

    @PostMapping("/login/2fa")
    public String verify(@RequestParam(defaultValue = "") String code, @RequestParam(defaultValue = "false") boolean recovery,
                         HttpServletRequest req, HttpServletResponse res, RedirectAttributes ra) {
        HttpSession session = req.getSession(false);
        Pending p = pending(session);
        if (p == null) return "redirect:/login?expired2fa";
        AdminUser u = users.findById(p.adminId()).orElse(null);
        if (u == null || !u.isEnabled()) {
            session.removeAttribute(PENDING);
            return "redirect:/login?disabled";
        }
        boolean ok = recovery ? twoFactor.useRecovery(u.getId(), code) : twoFactor.verify(u, code);
        if (!ok) {
            history.failure(u.getId(), u.getUsername(), recovery ? "恢复码错误" : "动态码错误", req);
            Pending next = p.failed();
            if (next.fails() >= MAX_TRIES) {
                session.removeAttribute(PENDING);
                audit.log("LOGIN_2FA_LOCKED", "管理员 " + u.getUsername() + " 两步验证连续失败 " + MAX_TRIES + " 次，需要重新登录");
                return "redirect:/login?locked2fa";
            }
            session.setAttribute(PENDING, next);
            ra.addFlashAttribute("error", recovery ? "恢复码不对，或者已经用过了" : "动态码不对，请输入身份验证器里最新的 6 位数字");
            ra.addFlashAttribute("recovery", recovery);
            return "redirect:/login/2fa";
        }
        session.removeAttribute(PENDING);
        if (recovery) {
            AdminUser fresh = users.findById(u.getId()).orElse(u);
            audit.logAs(u.getUsername(), null, "LOGIN_RECOVERY", "管理员 " + u.getUsername() + " 用恢复码登录，还剩 " + twoFactor.recoveryLeft(fresh) + " 个");
        }
        if (logins.mustPickDevices(req, u, true, p.remember())) return "redirect:/login/devices";
        UserDetails ud = userDetails.loadUserByUsername(u.getUsername());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(ud, null, ud.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        req.changeSessionId();
        contexts.saveContext(ctx, req, res);
        logins.finish(req, u, true, p.remember());
        return "redirect:/admin";
    }

    private static Pending pending(HttpSession s) {
        if (s == null) return null;
        Object o = s.getAttribute(PENDING);
        if (o instanceof Pending p && p.at().plus(TTL).isAfter(Instant.now())) return p;
        if (o != null) s.removeAttribute(PENDING);
        return null;
    }
}
