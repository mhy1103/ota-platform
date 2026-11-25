package com.ota.platform.admin;

import com.ota.platform.app.OtaApp;
import com.ota.platform.settings.SettingService;
import com.ota.platform.web.admin.AdminShell;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 后台每个请求都过这里：确认管理员还有效（停用、改密码后旧会话作废），再按下表检查权限。
 * 没列出来的写操作只有超级管理员能做；查看页面只要能看到当前 App 就行。
 */
@Component
public class AccessInterceptor implements HandlerInterceptor {

    public static final String SESSION_AUTH_AT = "admin.authAt";

    /**
     * 要求：OWNER 超级管理员，AGENTS 管理代理，CODES 口令与激活，CODES_VIEW 有口令权限或者能看至少一个 App，
     * VIEW 能看到当前 App，SELF 控制器里自己判断，其余是 App 权限
     */
    private static final String OWNER = "OWNER", AGENTS = "AGENTS", CODES = "CODES", CODES_VIEW = "CODES_VIEW", VIEW = "VIEW", SELF = "SELF";

    private static final Map<String, String> RULES = new LinkedHashMap<>();

    static {
        // 系统。系统配置页成员也能进，只显示外观（自己选一套，只对自己生效），控制器里分开处理
        rule("GET /admin/settings", SELF);
        rule("POST /admin/settings/appearance", SELF);
        rule("* /admin/settings/**", OWNER);
        rule("* /admin/admins/**", OWNER);
        rule("POST /admin/apps/create", OWNER);
        rule("POST /admin/apps/delete", OWNER);
        rule("POST /admin/logs/clear", OWNER);
        rule("POST /admin/storage/**", OWNER);
        // 代理
        rule("* /admin/agents/**", AGENTS);
        // 自己的账号、令牌、恢复、签名复用在控制器里判断
        rule("* /admin/account/**", SELF);
        rule("POST /admin/apps/token", SELF);
        rule("POST /admin/apps/token/*/revoke", SELF);
        rule("POST /admin/apps/restore", SELF);
        rule("POST /admin/signing/reuse", SELF);
        // 分享数据大屏：只能分享自己看得到的 App，自己的分享自己取消，在控制器里判断
        rule("* /admin/screen/shares/**", SELF);
        // 发布版本
        for (String p : new String[]{"/admin/upload", "/admin/addUrl", "/admin/deploy", "/admin/rollout", "/admin/rollback",
                "/admin/rollback-embedded", "/admin/release/edit", "/admin/delete", "/admin/guard", "/admin/activation/clear"}) {
            rule("POST " + p, AppPerm.RELEASE.name());
        }
        rule("POST /admin/addUrl/**", AppPerm.RELEASE.name());
        rule("POST /admin/installers/**", AppPerm.RELEASE.name());
        rule("GET /admin/installers/*/download", AppPerm.RELEASE.name());
        // 口令与激活（所有 App 通用）
        rule("POST /admin/code/**", CODES);
        rule("GET /admin/activations/export", CODES);
        rule("GET /admin/codes", CODES_VIEW);
        rule("GET /admin/activations", CODES_VIEW);
        rule("GET /admin/devices/export", VIEW);
        // 运行模式
        rule("POST /admin/set", AppPerm.MODE.name());
        // 计划任务：定时切换要运行模式权限，定时调灰度要发布权限，取消在控制器里按任务判断
        rule("POST /admin/schedules/mode", AppPerm.MODE.name());
        rule("POST /admin/schedules/rollout", AppPerm.RELEASE.name());
        rule("POST /admin/schedules/*/cancel", SELF);
        // App 设置
        for (String p : new String[]{"/admin/apps/rename", "/admin/apps/download-url", "/admin/apps/patch", "/admin/apps/channel", "/admin/apps/channel/delete",
                "/admin/apps/webhook", "/admin/apps/webhook/test", "/admin/apps/webhook/rotate", "/admin/apps/codes-key/rotate",
                "/admin/apps/notify", "/admin/apps/notify/test", "/admin/apps/notify/events", "/admin/apps/testers", "/admin/apps/testers/delete",
                "/admin/apps/icon", "/admin/apps/icon/delete"}) {
            rule("POST " + p, AppPerm.SETTINGS.name());
        }
        // 签名与备份
        rule("POST /admin/signing/**", AppPerm.SIGNING.name());
        rule("POST /admin/apps/backup", AppPerm.SIGNING.name());
        // 只读操作
        rule("POST /admin/verify", VIEW);
        rule("POST /admin/diagnose", VIEW);
    }

    private static void rule(String key, String need) {
        RULES.put(key, need);
    }

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final AccessService access;
    private final AdminUserService users;
    private final AdminShell shell;
    private final SettingService settings;

    public AccessInterceptor(AccessService access, AdminUserService users, AdminShell shell, SettingService settings) {
        this.access = access;
        this.users = users;
        this.shell = shell;
        this.settings = settings;
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws IOException {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return true;
        HttpSession session = req.getSession(false);
        AdminUser user = users.find(auth.getName()).orElse(null);
        if (user == null) {
            logout(req, res, "/login?expired");
            return false;
        }
        if (!user.isEnabled()) {
            logout(req, res, "/login?disabled");
            return false;
        }
        if (session != null) {
            Object at = session.getAttribute(SESSION_AUTH_AT);
            if (!(at instanceof Instant authAt)) {
                session.setAttribute(SESSION_AUTH_AT, Instant.now());
            } else if (user.getCredentialsAt().isAfter(authAt)) {
                logout(req, res, "/login?changed");
                return false;
            }
        }
        CurrentAdmin me = access.current();
        if (me == null) {
            logout(req, res, "/login?disabled");
            return false;
        }
        // 超级管理员设的初始密码，要先改成自己的密码才能用其他功能
        if (user.isMustChangePassword() && !req.getServletPath().startsWith("/admin/account")) {
            onlyAccount(req, res, "管理员为你设置的是初始密码，请先改成只有你自己知道的密码", "/admin/account");
            return false;
        }
        // 系统要求两步验证时，没开的人只能进「我的账号」把它开起来
        if (settings.require2fa() && !user.isTotpEnabled() && !req.getServletPath().startsWith("/admin/account")) {
            onlyAccount(req, res, "系统要求所有管理员开启两步验证，开启后就能正常使用后台", "/admin/account#security");
            return false;
        }

        String need = need(req.getMethod(), req.getServletPath());
        if (need == null || SELF.equals(need)) return true;
        String why = null;
        if (OWNER.equals(need)) {
            if (!me.owner()) why = "只有超级管理员能做这个操作";
        } else if (AGENTS.equals(need)) {
            if (!me.canManageAgents()) why = "没有管理代理的权限";
        } else if (CODES.equals(need)) {
            if (!me.canCodes()) why = "没有「口令与激活」权限，请联系超级管理员";
        } else if (CODES_VIEW.equals(need)) {
            if (!me.canCodes() && me.grants().isEmpty()) why = "还没有分配 App，请联系超级管理员";
        } else {
            OtaApp app = session == null ? null : shell.current(session);
            if (app == null) {
                if (!me.owner() && !VIEW.equals(need)) why = "没有可以操作的 App";
            } else if (!VIEW.equals(need)) {
                AppPerm perm = AppPerm.valueOf(need);
                if (!me.can(app.getId(), perm)) why = "没有「" + perm.getLabel() + "」权限，请联系超级管理员";
            }
        }
        if (why == null) return true;
        deny(req, res, why);
        return false;
    }

    /** 这个请求要什么权限；没在表里的 GET 只要能登录，写操作只有超级管理员 */
    String need(String method, String path) {
        for (Map.Entry<String, String> e : RULES.entrySet()) {
            int sp = e.getKey().indexOf(' ');
            String m = e.getKey().substring(0, sp);
            String p = e.getKey().substring(sp + 1);
            if (("*".equals(m) || m.equalsIgnoreCase(method)) && matcher.match(p, path)) return e.getValue();
        }
        return "GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method) ? null : OWNER;
    }

    private void deny(HttpServletRequest req, HttpServletResponse res, String why) throws IOException {
        boolean xhr = "XMLHttpRequest".equals(req.getHeader("X-Requested-With"))
                || (req.getHeader("Accept") != null && req.getHeader("Accept").contains("application/json"));
        if (xhr) {
            res.setStatus(403);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"ok\":false,\"msg\":\"" + why + "\"}");
            return;
        }
        String back = req.getHeader("Referer");
        String target = "/admin";
        if (back != null) {
            int i = back.indexOf("/admin");
            if (i >= 0) target = back.substring(i).replaceAll("[\\r\\n]", "");
        }
        if ("GET".equalsIgnoreCase(req.getMethod()) && target.startsWith(req.getServletPath())) target = "/admin";
        if ("/admin".equals(target) && shell.visible().isEmpty()) target = "/admin/apps";
        RequestContextUtils.getOutputFlashMap(req).put("error", why);
        RequestContextUtils.saveOutputFlashMap(target.split("[?#]")[0], req, res);
        res.sendRedirect(target);
    }

    private void onlyAccount(HttpServletRequest req, HttpServletResponse res, String why, String to) throws IOException {
        boolean xhr = "XMLHttpRequest".equals(req.getHeader("X-Requested-With"))
                || (req.getHeader("Accept") != null && req.getHeader("Accept").contains("application/json"));
        if (xhr) {
            res.setStatus(403);
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"ok\":false,\"msg\":\"" + why + "\"}");
            return;
        }
        RequestContextUtils.getOutputFlashMap(req).put("error", why);
        RequestContextUtils.saveOutputFlashMap("/admin/account", req, res);
        res.sendRedirect(to);
    }

    private static void logout(HttpServletRequest req, HttpServletResponse res, String to) throws IOException {
        HttpSession s = req.getSession(false);
        if (s != null) s.invalidate();
        SecurityContextHolder.clearContext();
        res.sendRedirect(to);
    }
}
