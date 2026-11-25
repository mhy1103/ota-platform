package com.ota.platform.security;

import com.ota.platform.settings.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.session.security.web.authentication.SpringSessionRememberMeServices;
import org.springframework.stereotype.Component;

/**
 * 登录保持多久：勾了「记住我」，会话保持「系统配置 → 登录安全」里设的天数，关掉浏览器也不丢；
 * 没勾就是空闲一段时间自动退出，关掉浏览器就要重新登录。会话存在数据库里，重启服务不影响。
 */
@Component
public class LoginLifetime {

    public static final String PARAM = "remember-me";

    private final SettingService settings;

    public LoginLifetime(SettingService settings) {
        this.settings = settings;
    }

    public static boolean wanted(HttpServletRequest req) {
        String v = req.getParameter(PARAM);
        return v != null && !v.isBlank() && !"false".equalsIgnoreCase(v) && !"off".equalsIgnoreCase(v);
    }

    /** 登录完成、写 Cookie 之前调用 */
    public void apply(HttpServletRequest req, boolean remember) {
        HttpSession s = req.getSession();
        if (remember) {
            s.setMaxInactiveInterval(settings.rememberDays() * 86_400);
            req.setAttribute(SpringSessionRememberMeServices.REMEMBER_ME_LOGIN_ATTR, Boolean.TRUE);
        } else {
            s.setMaxInactiveInterval(settings.idleMinutes() * 60);
        }
    }
}
