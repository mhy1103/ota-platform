package com.ota.platform.security;

import com.ota.platform.admin.AccessInterceptor;
import com.ota.platform.admin.AdminUser;
import com.ota.platform.admin.AdminUserService;
import com.ota.platform.audit.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;

import java.time.Instant;

/** 管理员登录完成后要做的事：密码登录和两步验证登录都走这里 */
@Component
public class AdminLogins {


    private final AdminUserService users;
    private final AuditService audit;
    private final LoginHistory history;
    private final AdminSessions sessions;
    private final LoginLifetime lifetime;
    private final boolean demo;
    private final int startupMax;
    private final com.ota.platform.settings.SettingService settings;

    public AdminLogins(AdminUserService users, AuditService audit, LoginHistory history, AdminSessions sessions, LoginLifetime lifetime,
                       @org.springframework.beans.factory.annotation.Value("${ota.demo.mode:false}") boolean demo,
                       @org.springframework.beans.factory.annotation.Value("${ota.login.max-devices:10}") int maxDevices,
                       com.ota.platform.settings.SettingService settings) {
        this.demo = demo;
        this.startupMax = Math.max(1, maxDevices);
        this.settings = settings;
        this.users = users;
        this.audit = audit;
        this.history = history;
        this.sessions = sessions;
        this.lifetime = lifetime;
    }

    /** 密码（和两步验证）都对了，但登录设备已经满了：先不算登录，去选退出哪几台。演示账号大家共用，不限 */
    public boolean mustPickDevices(HttpServletRequest req, AdminUser u, boolean twoFactor, boolean remember) {
        if (demo || sessions.count(u.getId(), u.getUsername()) < maxDevices()) return false;
        DeviceLimitController.hold(req, u, twoFactor, remember);
        return true;
    }

    /** 每人最多同时登录几台：系统配置里改过的优先，否则用 ota.login.max-devices */
    public int maxDevices() {
        return settings.maxDevices(startupMax);
    }

    public void finish(HttpServletRequest req, AdminUser u, boolean twoFactor, boolean remember) {
        users.recordLogin(u.getUsername());
        HttpSession s = req.getSession();
        s.setAttribute(AccessInterceptor.SESSION_AUTH_AT, Instant.now());
        lifetime.apply(req, remember);
        sessions.register(s, u.getId(), u.getUsername(), req.getHeader("User-Agent"));
        audit.log("ADMIN_LOGIN", "管理员 " + u.getUsername() + " 登录" + (twoFactor ? "（两步验证）" : ""));
        history.success(u.getId(), u.getUsername(), req);
    }
}
