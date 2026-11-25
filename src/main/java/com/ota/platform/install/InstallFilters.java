package com.ota.platform.install;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * 两道最先执行的关卡：
 * 1. 域名：后台、接口可以绑不同的域名，用错域名访问一律 404；
 * 2. 安装：没装好之前后台页面都转到安装引导，App 用的接口照常响应；装好以后安装引导关闭，超级管理员在系统配置里重新打开才行。
 */
@Configuration
public class InstallFilters {

    public static final String REOPEN = "ota.install.reopen";
    static final Duration REOPEN_TTL = Duration.ofMinutes(30);

    /** App 和命令行用的地址：清单、启动包、文件、口令接口、管理接口 */
    static boolean apiPath(String path) {
        return path.startsWith("/m/") || path.startsWith("/l/") || path.startsWith("/a/") || path.startsWith("/v1/")
                || path.startsWith("/api/") || path.equals("/api");
    }

    private static void json(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"ok\":false,\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }

    public static boolean reopened(HttpSession session) {
        if (session == null) return false;
        Object at = session.getAttribute(REOPEN);
        return at instanceof Instant t && t.plus(REOPEN_TTL).isAfter(Instant.now());
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> domainGuard(InstallService install) {
        OncePerRequestFilter f = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                String path = req.getServletPath();
                // 官网（根路径和它的样式、脚本）哪个域名都能打开
                InstallService.Role role = path.equals("/healthz") || path.equals("/") || path.startsWith("/site/") ? InstallService.Role.ANY
                        : apiPath(path) ? InstallService.Role.API : InstallService.Role.ADMIN;
                if (install.allows(req.getServerName(), role)) {
                    chain.doFilter(req, res);
                    return;
                }
                res.setStatus(404);
                if (role == InstallService.Role.API) {
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"error\":\"NOT_FOUND\",\"message\":\"这个域名没有开放接口\"}");
                } else {
                    res.setContentType("text/plain;charset=UTF-8");
                    res.getWriter().write("404 Not Found");
                }
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(f);
        reg.setOrder(-300);
        reg.addUrlPatterns("/*");
        return reg;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> installGate(InstallService install) {
        OncePerRequestFilter f = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                String path = req.getServletPath();
                boolean installPath = path.equals("/install") || path.startsWith("/install/");
                boolean open = path.equals("/healthz") || path.startsWith("/vue/assets/") || path.equals("/favicon.ico")
                        || path.equals("/error") || apiPath(path);
                if (!install.installed()) {
                    if (installPath || open) {
                        chain.doFilter(req, res);
                    } else if ("GET".equals(req.getMethod())) {
                        res.sendRedirect("/install");
                    } else {
                        json(res, 503, "NOT_INSTALLED", "还没有安装，请先完成安装引导");
                    }
                    return;
                }
                if (installPath && !reopened(req.getSession(false)) && !path.equals("/install/status")) {
                    if ("GET".equals(req.getMethod())) res.sendRedirect("/admin");
                    else json(res, 403, "INSTALLED", "已经安装过了。要修改安装配置，请在系统配置里重新打开安装引导");
                    return;
                }
                chain.doFilter(req, res);
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(f);
        reg.setOrder(-200);
        reg.addUrlPatterns("/*");
        return reg;
    }
}
