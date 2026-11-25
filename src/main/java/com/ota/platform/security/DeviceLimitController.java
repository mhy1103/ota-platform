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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 登录设备满了：密码和两步验证都对了，先选退出哪几台设备，选够了才算登录完成 */
@Controller
public class DeviceLimitController {

    public static final String PENDING = "ota.devices.pending";
    private static final Duration TTL = Duration.ofMinutes(10);

    public record Pending(Long adminId, String username, Instant at, boolean twoFactor, boolean remember) implements Serializable {}

    private final AdminUserRepo users;
    private final AdminSessions sessions;
    private final UserDetailsService userDetails;
    private final AdminLogins logins;
    private final AuditService audit;
    private final SecurityContextRepository contexts = new HttpSessionSecurityContextRepository();

    public DeviceLimitController(AdminUserRepo users, AdminSessions sessions, UserDetailsService userDetails, AdminLogins logins, AuditService audit) {
        this.users = users;
        this.sessions = sessions;
        this.userDetails = userDetails;
        this.logins = logins;
        this.audit = audit;
    }

    /** 先不算登录，记下是谁、是不是已经过了两步验证 */
    public static void hold(HttpServletRequest req, AdminUser u, boolean twoFactor, boolean remember) {
        HttpSession s = req.getSession();
        s.removeAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        SecurityContextHolder.clearContext();
        s.setAttribute(PENDING, new Pending(u.getId(), u.getUsername(), Instant.now(), twoFactor, remember));
    }

    @GetMapping("/login/devices")
    public String page(HttpSession session, Model model) {
        Pending p = pending(session);
        if (p == null) return "redirect:/login?expired";
        List<AdminSessions.Info> list = sessions.of(p.adminId(), p.username(), session.getId());
        int need = list.size() - logins.maxDevices() + 1;
        if (need <= 0) return "redirect:/login?expired";
        model.addAttribute("username", p.username());
        model.addAttribute("devices", list);
        model.addAttribute("max", logins.maxDevices());
        model.addAttribute("need", need);
        return "login-devices";
    }

    @PostMapping("/login/devices")
    public String choose(@RequestParam(name = "keys", required = false) List<String> keys, HttpServletRequest req, HttpServletResponse res,
                         RedirectAttributes ra) {
        HttpSession session = req.getSession(false);
        Pending p = pending(session);
        if (p == null) return "redirect:/login?expired";
        AdminUser u = users.findById(p.adminId()).orElse(null);
        if (u == null || !u.isEnabled()) {
            session.removeAttribute(PENDING);
            return "redirect:/login?disabled";
        }
        Set<String> picked = keys == null ? Set.of() : new HashSet<>(keys);
        int need = sessions.count(u.getId(), u.getUsername()) - logins.maxDevices() + 1;
        if (need > 0) {
            if (picked.size() < need) {
                ra.addFlashAttribute("error", "至少选 " + need + " 台设备退出");
                return "redirect:/login/devices";
            }
            int n = sessions.revokeKeys(u.getId(), u.getUsername(), picked);
            if (n < need) {
                ra.addFlashAttribute("error", "选的设备已经退出了或者不存在，请重新选");
                return "redirect:/login/devices";
            }
            audit.logAs(u.getUsername(), null, "SESSION_REVOKE", "管理员 " + u.getUsername() + " 登录设备已满，登录时退出了 " + n + " 台设备");
        }
        session.removeAttribute(PENDING);
        UserDetails ud = userDetails.loadUserByUsername(u.getUsername());
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(ud, null, ud.getAuthorities()));
        SecurityContextHolder.setContext(ctx);
        req.changeSessionId();
        contexts.saveContext(ctx, req, res);
        logins.finish(req, u, p.twoFactor(), p.remember());
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
