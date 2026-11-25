package com.ota.platform.web.page;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.regex.Pattern;

/**
 * 浏览器直接打开页面（地址栏、刷新、查看源代码）时只给一个加载壳：居中的加载动画和入口脚本。
 * 页面数据由脚本带 X-OTA-Data 头取回来（见 PageData），再用 Vue 渲染，所以「查看源代码」看不到页面内容。
 * 下载文件、接口、脚本发起的请求都不经过这里。
 */
@Configuration
public class VueUi {

    private final com.ota.platform.web.Prefs prefs;

    public VueUi(com.ota.platform.web.Prefs prefs) {
        this.prefs = prefs;
    }

    /** 走加载壳的页面（/s/ 是分享出去的数据大屏）；根路径是官网，自己就是完整的页面，不走加载壳 */
    private static final Pattern PAGE = Pattern.compile("^(/login|/login/2fa|/install|/admin(/.*)?|/agent(/.*)?|/s/[A-Za-z0-9_-]{8,64})$");
    private static final Pattern NOT_PAGE = Pattern.compile(".*(/export|/download|\\.[A-Za-z0-9]{2,5})$");
    private static final String LOGO = "<svg class=\"ob-logo\" viewBox=\"0 0 24 24\" aria-hidden=\"true\"><rect x=\"1\" y=\"1\" width=\"22\" height=\"22\" rx=\"7\"/>"
            + "<g class=\"ob-orbit\"><path d=\"M13.35 7.48A5.2 5.2 0 1 0 17.18 12.95\"/><circle cx=\"16.6\" cy=\"9.3\" r=\"1.6\"/></g></svg>";
    private static final String CSS = ":root{color-scheme:light;--b:#f7f7f8;--i:#0f0f11;--t:#e6e6e9}"
            + ":root[data-mode=dark]{color-scheme:dark;--b:#0b0b0d;--i:#ededef;--t:#232328}"
            + ".ob-wait,.ob-wait body{margin:0;min-height:100%;background:var(--b)}"
            + ".ob{position:fixed;inset:0;z-index:2147483000;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:26px;background:var(--b);"
            + "opacity:0;animation:ob-in .35s ease .15s forwards;transition:opacity .4s ease,visibility .4s}"
            + ".ob.out{opacity:0!important;visibility:hidden;animation:none}.ob.q{transition-duration:.2s}"
            + ".ob-logo{width:46px;height:46px}.ob-logo rect{fill:var(--i)}"
            + ".ob-orbit{transform-origin:12px 12.5px;animation:ob-spin 1.6s cubic-bezier(.65,0,.35,1) infinite}"
            + ".ob-orbit path{fill:none;stroke:var(--b);stroke-width:2.2;stroke-linecap:round}.ob-orbit circle{fill:var(--b)}"
            + ".ob-bar{width:132px;height:2px;border-radius:2px;background:var(--t);overflow:hidden}"
            + ".ob-bar i{display:block;width:34%;height:100%;border-radius:2px;background:var(--i);animation:ob-slide 1.15s ease-in-out infinite}"
            + ".ob.p .ob-bar i{width:0;animation:none;transition:width .35s cubic-bezier(.16,1,.3,1)}"
            + ".ob-msg{margin:0;font:13px/1.6 -apple-system,BlinkMacSystemFont,'PingFang SC','Microsoft YaHei',sans-serif;color:var(--i);opacity:.55;min-height:21px;text-align:center;padding:0 24px}"
            + ".ob-msg button{margin-left:6px;font:inherit;color:inherit;background:none;border:1px solid currentColor;border-radius:6px;padding:1px 8px;cursor:pointer}"
            + "@keyframes ob-in{to{opacity:1}}@keyframes ob-spin{to{transform:rotate(360deg)}}"
            + "@keyframes ob-slide{0%{transform:translateX(-110%)}100%{transform:translateX(300%)}}"
            + "@media (prefers-reduced-motion:reduce){.ob-orbit,.ob-bar i{animation:none}}";
    static final String FAVICON = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'%3E%3Crect x='1' y='1' width='22' height='22' rx='7' fill='%230f0f11'/%3E"
            + "%3Cpath d='M13.35 7.48A5.2 5.2 0 1 0 17.18 12.95' fill='none' stroke='%23fff' stroke-width='2.2' stroke-linecap='round'/%3E%3Ccircle cx='16.6' cy='9.3' r='1.6' fill='%23fff'/%3E%3C/svg%3E";

    private volatile Entry entry;

    /** main：入口脚本；app：登录后才用的后台脚本（登录了就预先加载）；build：打包版本，给 boot.js 加版本号 */
    private record Entry(String main, String app, String build) {}

    /** 打包时 Vite 生成的入口文件名（带内容哈希） */
    private Entry entry() {
        Entry e = entry;
        if (e != null) return e;
        String main = null, app = null, build = "0";
        ClassPathResource res = new ClassPathResource("static/vue/.vite/manifest.json");
        if (res.exists()) {
            try (InputStream in = res.getInputStream()) {
                byte[] raw = in.readAllBytes();
                build = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw)).substring(0, 10);
                for (JsonNode chunk : new ObjectMapper().readTree(raw)) {
                    String file = chunk.path("file").asText();
                    if (chunk.path("isEntry").asBoolean()) main = "/vue/" + file;
                    if ("app".equals(chunk.path("name").asText()) && file.startsWith("app/")) app = "/vue/" + file;
                }
            } catch (Exception ignored) {
                // 清单坏了下面会报错
            }
        }
        if (main == null) throw new IllegalStateException("没有找到前端打包结果（static/vue/.vite/manifest.json），请用 ./build.sh 打包");
        entry = new Entry(main, app, build);
        return entry;
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> pageShell() {
        OncePerRequestFilter f = new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
                if ("GET".equals(req.getMethod()) && browser(req) && PAGE.matcher(req.getServletPath()).matches()
                        && !NOT_PAGE.matcher(req.getServletPath()).matches()) {
                    shell(res, 200);
                } else {
                    chain.doFilter(req, res);
                }
            }
        };
        FilterRegistrationBean<OncePerRequestFilter> reg = new FilterRegistrationBean<>(f);
        reg.setOrder(10);
        reg.addUrlPatterns("/*");
        return reg;
    }

    /**
     * 浏览器自己打开的（地址栏、刷新、表单原生提交），不是脚本发的请求。
     * 安卓 WebView 做的浏览器（UC、QQ 浏览器、夸克、小米、华为）和微信里打开的网页，每个请求都带 X-Requested-With: 应用包名，
     * 只有 XMLHttpRequest（和以前的 ota-nav）才是脚本发的
     */
    static boolean browser(HttpServletRequest req) {
        if (req.getHeader(PageData.HEADER) != null) return false;
        String xrw = req.getHeader("X-Requested-With");
        if (xrw != null && (xrw.equalsIgnoreCase("XMLHttpRequest") || xrw.equals("ota-nav"))) return false;
        String dest = req.getHeader("Sec-Fetch-Dest");
        if (dest != null) return "document".equals(dest);
        String accept = req.getHeader("Accept");
        return accept != null && accept.contains("text/html");
    }

    /** 加载壳。状态码照原样（打开不存在的地址时是 404），脚本再去取同一个地址的页面数据 */
    void shell(HttpServletResponse res, int status) throws IOException {
        Entry e = entry();
        String preload = e.app() != null && signedIn() ? "<link rel=\"modulepreload\" href=\"" + e.app() + "\">" : "";
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String save = prefs.saveUrl(auth);
        String stored = save == null ? null : prefs.stored(auth);
        String prefsTag = save == null ? "" : "<meta name=\"ota-prefs\" content=\"" + attr(stored == null ? "null" : prefs.forPage(stored))
                + "\" data-save=\"" + save + "\">";
        String html = "<!DOCTYPE html><html lang=\"zh-CN\" class=\"ob-wait\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"><meta name=\"robots\" content=\"noindex\">"
                + "<title>管理后台</title><link rel=\"icon\" href=\"" + FAVICON + "\">"
                + prefsTag + "<script src=\"/vue/assets/boot.js?v=" + e.build() + "\"></script><style>" + CSS + "</style>"
                + "<script type=\"module\" src=\"" + e.main() + "\"></script>" + preload + "</head>"
                + "<body><div class=\"ob\" id=\"ota-boot\" role=\"progressbar\" aria-label=\"正在加载\">" + LOGO
                + "<div class=\"ob-bar\"><i></i></div><p class=\"ob-msg\"></p></div></body></html>";
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        res.setStatus(status);
        res.setContentType("text/html;charset=UTF-8");
        res.setHeader("Cache-Control", "no-cache, no-store, must-revalidate");
        res.setHeader("Vary", "Sec-Fetch-Dest, X-Requested-With, " + PageData.HEADER);
        res.setContentLength(body.length);
        res.getOutputStream().write(body);
    }

    private static String attr(String s) {
        return s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static boolean signedIn() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.isAuthenticated() && !(a instanceof AnonymousAuthenticationToken);
    }
}
