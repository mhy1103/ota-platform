package com.ota.platform.demo;

import com.ota.platform.admin.AdminUserRepo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 演示环境里不让做的事：会把大家共用的演示账号锁住的（改密码、开两步验证、踢掉别人）、
 * 会让服务器往外发请求或者动到服务器本身的（存储、域名、对外地址、重装、整体恢复、外部链接）。
 * 其余操作随便点，数据都在演示环境自己的目录里，每天恢复默认。
 */
@Configuration
@ConditionalOnProperty(name = "ota.demo.mode", havingValue = "true")
public class DemoGuard implements WebMvcConfigurer, HandlerInterceptor {

    private static final AntPathMatcher MATCH = new AntPathMatcher();
    private static final Map<String, String> BLOCK = Map.ofEntries(
            Map.entry("POST /admin/account", "演示账号是大家共用的，不能改账号和密码"),
            Map.entry("POST /admin/account/2fa/**", "演示账号是大家共用的，不能开两步验证"),
            Map.entry("POST /admin/account/sessions/revoke-others", "演示账号是大家共用的，不能让别人下线"),
            Map.entry("POST /admin/settings/security", "演示环境不能改登录安全"),
            Map.entry("POST /admin/settings/domains", "演示环境不能改访问域名"),
            Map.entry("POST /admin/settings/public-url", "演示环境不能改对外地址"),
            Map.entry("POST /admin/settings/storage/**", "演示环境不能切换文件存储"),
            Map.entry("POST /admin/settings/reinstall", "演示环境不能重新安装"),
            Map.entry("POST /admin/settings/backup/restore/**", "演示环境不能恢复整个项目"),
            Map.entry("POST /admin/settings/backup/stored/*/restore", "演示环境不能恢复整个项目"),
            Map.entry("POST /admin/addUrl", "演示环境不能从外部链接下载更新包，上传更新包试试吧"),
            Map.entry("POST /admin/screen/shares/**", "演示环境不能分享数据大屏"),
            Map.entry("POST /install/**", "演示环境不能重新安装"));
    private static final List<String> SELF = List.of("/admin/admins/{id}/update", "/admin/admins/{id}/delete", "/admin/admins/{id}/2fa/reset");

    private final AdminUserRepo users;
    private final String username;

    public DemoGuard(AdminUserRepo users, @Value("${ota.demo.admin:demo}") String username) {
        this.users = users;
        this.username = username;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).order(Integer.MIN_VALUE);
    }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        String path = req.getRequestURI(), method = req.getMethod();
        String why = null;
        for (Map.Entry<String, String> e : BLOCK.entrySet()) {
            String[] rule = e.getKey().split(" ", 2);
            if (rule[0].equals(method) && MATCH.match(rule[1], path)) {
                why = e.getValue();
                break;
            }
        }
        if (why == null && "POST".equals(method)) {
            for (String p : SELF) {
                if (!MATCH.match(p, path)) continue;
                String id = MATCH.extractUriTemplateVariables(p, path).get("id");
                boolean self = users.findByUsernameIgnoreCase(username).map(u -> String.valueOf(u.getId()).equals(id)).orElse(false);
                if (self) why = "演示账号是大家共用的，不能改它自己";
            }
        }
        if (why == null) return true;
        String accept = req.getHeader("Accept"), xhr = req.getHeader("X-Requested-With");
        boolean json = (accept != null && accept.contains("application/json") && !accept.contains("text/html"))
                || ("XMLHttpRequest".equals(xhr) && (accept == null || !accept.contains("text/html")));
        if (json) {
            res.setStatus(403);
            res.setContentType("application/json;charset=UTF-8");
            res.getOutputStream().write(("{\"ok\":false,\"msg\":\"" + why + "\"}").getBytes(StandardCharsets.UTF_8));
            return false;
        }
        String referer = req.getHeader("Referer");
        String back = "/admin";
        if (referer != null) {
            int i = referer.indexOf("/admin");
            if (i >= 0) back = referer.substring(i);
        }
        RequestContextUtils.getOutputFlashMap(req).put("error", why);
        RequestContextUtils.saveOutputFlashMap(back.replaceAll("[?#].*$", ""), req, res);
        res.sendRedirect(back);
        return false;
    }
}
