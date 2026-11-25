package com.ota.platform.demo;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * 演示账号的请求转给演示环境：浏览器带着 OTA_DEMO（演示环境的会话号）时，除了公开的静态文件，所有请求原样转过去。
 * 用演示账号密码登录时在这里拦下来，启动演示环境、代为登录、发 OTA_DEMO 给浏览器。
 * 打开登录页就是离开演示环境；演示环境说会话失效（跳登录页）时把 OTA_DEMO 清掉。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "ota.demo.mode", havingValue = "false", matchIfMissing = true)
public class DemoProxyFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(DemoProxyFilter.class);
    public static final String COOKIE = "OTA_DEMO";
    /** HttpClient 不让设的、逐跳的请求头 */
    private static final Set<String> SKIP_REQUEST = Set.of("connection", "content-length", "date", "expect", "from", "host", "upgrade",
            "via", "warning", "keep-alive", "proxy-connection", "proxy-authorization", "te", "trailer", "transfer-encoding", "cookie",
            "x-forwarded-host", "x-forwarded-proto", "x-forwarded-port", "x-forwarded-for", "forwarded");
    private static final Set<String> SKIP_RESPONSE = Set.of("connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade",
            "proxy-authenticate", ":status", "set-cookie", "content-length");

    private final DemoSandbox sandbox;

    public DemoProxyFilter(DemoSandbox sandbox) {
        this.sandbox = sandbox;
    }

    /** 排在会话和登录过滤器前面：演示环境的请求不碰正式环境的会话 */
    @Bean
    public FilterRegistrationBean<DemoProxyFilter> demoProxyRegistration() {
        FilterRegistrationBean<DemoProxyFilter> r = new FilterRegistrationBean<>(this);
        r.setOrder(Integer.MIN_VALUE + 10);
        r.addUrlPatterns("/*");
        r.setName("demoProxy");
        return r;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        String path = req.getRequestURI();
        String sid = cookie(req);
        if (sid == null || !sandbox.enabled()) {
            if (sid != null) expire(req, res);
            if ("POST".equals(req.getMethod()) && "/login".equals(path) && sandbox.enabled()
                    && sandbox.matches(req.getParameter("username"), req.getParameter("password"))) {
                demoLogin(req, res);
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        // 打开登录页、安装页 = 离开演示环境，回到正式环境
        if (path.equals("/login") || path.startsWith("/login/") || path.startsWith("/install")) {
            expire(req, res);
            if ("POST".equals(req.getMethod()) && "/login".equals(path)
                    && sandbox.matches(req.getParameter("username"), req.getParameter("password"))) {
                demoLogin(req, res);
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        // 页面的 WebSocket 连在正式环境上：它代为发的请求带着 OTA_DEMO，会照常从这里转给演示环境
        if (path.startsWith("/vue/assets/") || path.equals("/favicon.ico") || path.equals("/healthz") || path.equals(com.ota.platform.web.page.LiveSocket.PATH)) {
            chain.doFilter(request, response);
            return;
        }
        proxy(req, res, sid);
    }

    private void demoLogin(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String sid = sandbox.login(req.getHeader("Host"), proto(req));
            Cookie c = new Cookie(COOKIE, sid);
            c.setPath("/");
            c.setHttpOnly(true);
            c.setSecure(req.isSecure() || "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto")));
            c.setAttribute("SameSite", "Lax");
            res.addCookie(c);
            res.sendRedirect("/admin");
        } catch (Exception e) {
            log.warn("演示环境没启动起来", e);
            res.sendRedirect("/login?demo-error");
        }
    }

    private void proxy(HttpServletRequest req, HttpServletResponse res, String sid) throws IOException {
        int port;
        try {
            port = sandbox.ensureRunning();
        } catch (Exception e) {
            log.warn("演示环境没启动起来", e);
            expire(req, res);
            res.sendRedirect("/login?demo-error");
            return;
        }
        sandbox.touch();
        String qs = req.getQueryString();
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + req.getRequestURI() + (qs == null ? "" : "?" + qs)))
                .timeout(Duration.ofMinutes(30));
        for (Enumeration<String> names = req.getHeaderNames(); names.hasMoreElements(); ) {
            String name = names.nextElement();
            if (SKIP_REQUEST.contains(name.toLowerCase(Locale.ROOT))) continue;
            for (Enumeration<String> vs = req.getHeaders(name); vs.hasMoreElements(); ) b.header(name, vs.nextElement());
        }
        b.header("Cookie", "OTA_SESSION=" + sid);
        String host = req.getHeader("Host");
        if (host != null) b.header("X-Forwarded-Host", host);
        b.header("X-Forwarded-Proto", proto(req));
        String fwd = req.getHeader("X-Forwarded-For");
        b.header("X-Forwarded-For", fwd == null || fwd.isBlank() ? req.getRemoteAddr() : fwd + ", " + req.getRemoteAddr());
        String method = req.getMethod();
        boolean body = req.getContentLengthLong() > 0 || req.getHeader("Transfer-Encoding") != null;
        if (body) b.method(method, HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return req.getInputStream();
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }));
        else b.method(method, HttpRequest.BodyPublishers.noBody());
        HttpResponse<InputStream> r;
        try {
            r = sandbox.http().send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            res.sendError(503);
            return;
        } catch (IOException e) {
            log.debug("转给演示环境失败：{}", e.toString());
            res.sendError(502, "演示环境暂时连不上，请刷新重试");
            return;
        }
        res.setStatus(r.statusCode());
        final int child = port;
        r.headers().map().forEach((name, values) -> {
            if (SKIP_RESPONSE.contains(name.toLowerCase(Locale.ROOT))) return;
            for (String v : values) res.addHeader(name, "location".equalsIgnoreCase(name) ? local(v, host, child) : v);
        });
        r.headers().firstValueAsLong("content-length").ifPresent(n -> {
            if (r.headers().firstValue("content-encoding").isPresent()) res.setContentLengthLong(n);
        });
        for (String c : r.headers().allValues("set-cookie")) {
            if (c.startsWith("OTA_SESSION=")) res.addHeader("Set-Cookie", COOKIE + c.substring("OTA_SESSION".length()));
        }
        String location = r.headers().firstValue("location").orElse("");
        if (r.statusCode() / 100 == 3 && (location.startsWith("/login") || location.contains("://") && location.contains("/login"))) {
            expire(req, res);
        }
        try (InputStream in = r.body(); OutputStream out = res.getOutputStream()) {
            in.transferTo(out);
        }
    }

    /** 演示环境的跳转都在站内：去掉协议和域名，和正式环境一样用相对地址，反向代理没传 Host 时也不会跳到内网地址 */
    static String local(String location, String host, int port) {
        try {
            URI u = URI.create(location);
            String auth = u.getRawAuthority();
            if (u.getScheme() == null || auth == null) return location;
            if (!auth.equalsIgnoreCase(host == null ? "" : host) && !auth.equals("127.0.0.1:" + port)) return location;
            String path = u.getRawPath() == null || u.getRawPath().isEmpty() ? "/" : u.getRawPath();
            return path + (u.getRawQuery() == null ? "" : "?" + u.getRawQuery()) + (u.getRawFragment() == null ? "" : "#" + u.getRawFragment());
        } catch (IllegalArgumentException e) {
            return location;
        }
    }

    private static String proto(HttpServletRequest req) {
        String p = req.getHeader("X-Forwarded-Proto");
        return p != null && !p.isBlank() ? p.split(",")[0].trim() : req.getScheme();
    }

    private static String cookie(HttpServletRequest req) {
        Cookie[] cs = req.getCookies();
        if (cs == null) return null;
        for (Cookie c : cs) if (COOKIE.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) return c.getValue();
        return null;
    }

    private static void expire(HttpServletRequest req, HttpServletResponse res) {
        Cookie c = new Cookie(COOKIE, "");
        c.setPath("/");
        c.setMaxAge(0);
        c.setHttpOnly(true);
        c.setSecure(req.isSecure() || "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto")));
        res.addCookie(c);
    }
}
