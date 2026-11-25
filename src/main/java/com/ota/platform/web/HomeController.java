package com.ota.platform.web;

import com.ota.platform.settings.SettingService;
import com.ota.platform.web.page.BuildStamp;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.util.HtmlUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Year;

/** 根路径是官网（谁都能看），后台在 /admin，代理端在 /agent，登录在 /login */
@Controller
public class HomeController {

    private final SettingService settings;
    private final BuildStamp build;
    private volatile String page;

    public HomeController(SettingService settings, BuildStamp build) {
        this.settings = settings;
        this.build = build;
    }

    /** 官网是一个独立的静态页面：不用加载后台的脚本，打开就有；样式和脚本带内容哈希，改了浏览器就取新的 */
    @GetMapping("/")
    @ResponseBody
    public ResponseEntity<String> home() throws IOException {
        String html = template()
                .replace("{{brand}}", HtmlUtils.htmlEscape(brand()))
                .replace("{{v}}", version())
                .replace("{{year}}", String.valueOf(Year.now().getValue()));
        return ResponseEntity.ok().contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .cacheControl(CacheControl.noCache()).body(html);
    }

    /** 后台名字去掉「管理后台」「后台」，比如「OTA 管理后台」在官网上叫「OTA」 */
    private String brand() {
        String b = settings.brandName().replaceAll("\\s*(管理后台|后台|管理系统)\\s*$", "").trim();
        return b.isEmpty() ? "OTA" : b;
    }

    private volatile String version;

    /** 官网样式和脚本的内容哈希（/site 下的文件浏览器缓存一年） */
    private String version() {
        String v = version;
        if (v != null) return v;
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            for (String f : new String[]{"static/site/home.css", "static/site/home.js"}) {
                try (InputStream in = new ClassPathResource(f).getInputStream()) {
                    md.update(in.readAllBytes());
                }
            }
            v = java.util.HexFormat.of().formatHex(md.digest()).substring(0, 10);
        } catch (Exception e) {
            v = build.id();
        }
        version = v;
        return v;
    }

    private String template() throws IOException {
        String t = page;
        if (t == null) {
            try (InputStream in = new ClassPathResource("site/index.html").getInputStream()) {
                t = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
            page = t;
        }
        return t;
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("rememberDays", settings.rememberDays());
        model.addAttribute("lockMinutes", settings.lockMinutes());
        model.addAttribute("loginTitle", settings.loginTitle());
        model.addAttribute("loginText", settings.loginText());
        return "login";
    }
}
