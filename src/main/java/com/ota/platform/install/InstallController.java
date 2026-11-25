package com.ota.platform.install;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.platform.OtaPlatformApplication;
import com.ota.platform.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.util.*;

/** 安装引导：没安装时谁都能打开；装好以后只有超级管理员从「系统配置 → 安装与域名」重新打开才能用 */
@Controller
@RequestMapping("/install")
public class InstallController {

    private final InstallService install;
    private final ObjectMapper mapper;

    public InstallController(InstallService install, ObjectMapper mapper) {
        this.install = install;
        this.mapper = mapper;
    }

    private void guard(HttpServletRequest req) {
        if (install.installed() && !InstallFilters.reopened(req.getSession(false))) {
            throw ApiException.forbidden("已经安装过了，要重新安装请在「系统配置 → 安装与域名」里打开安装引导");
        }
    }

    @GetMapping
    public String page(Model model, HttpServletRequest req) {
        guard(req);
        InstallConfig c = install.snapshot();
        boolean reinstall = install.installed();
        model.addAttribute("reinstall", reinstall);
        model.addAttribute("cfg", c);
        model.addAttribute("host", req.getServerName() + (req.getServerPort() == 80 || req.getServerPort() == 443 ? "" : ":" + req.getServerPort()));
        model.addAttribute("https", "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto")) || req.isSecure());
        model.addAttribute("version", OtaPlatformApplication.version());
        model.addAttribute("cfgJson", prefill(c));
        return "install";
    }

    /** 重新安装时预填当前配置；密码、密钥只告诉前端「已设置」，不下发 */
    private String prefill(InstallConfig c) {
        Map<String, Object> v = new LinkedHashMap<>();
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("type", c.db == null ? "h2" : c.db.type);
        db.put("host", c.db == null ? "" : nz(c.db.host));
        db.put("port", c.db == null || c.db.port == null ? 3306 : c.db.port);
        db.put("name", c.db == null ? "" : nz(c.db.name));
        db.put("username", c.db == null ? "" : nz(c.db.username));
        db.put("hasPassword", c.db != null && c.db.password != null && !c.db.password.isBlank());
        v.put("db", db);
        v.put("restrictDomains", c.restrictDomains);
        v.put("domains", c.domains);
        v.put("publicUrl", nz(c.publicUrl));
        v.put("displayZone", c.displayZone == null || c.displayZone.isBlank() ? "Asia/Shanghai" : c.displayZone);
        Map<String, Object> st = new LinkedHashMap<>();
        InstallConfig.Storage s = c.storage == null ? new InstallConfig.Storage() : c.storage;
        st.put("type", s.type == null ? "local" : s.type);
        st.put("endpoint", nz(s.endpoint));
        st.put("region", nz(s.region));
        st.put("bucket", nz(s.bucket));
        st.put("accessKey", nz(s.accessKey));
        st.put("hasSecret", s.secretKey != null && !s.secretKey.isBlank());
        st.put("pathStyle", s.pathStyle);
        st.put("prefix", s.prefix == null ? "ota/" : s.prefix);
        st.put("publicUrl", nz(s.publicUrl));
        v.put("storage", st);
        try {
            return mapper.writeValueAsString(v);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 运行环境检查 */
    @PostMapping("/check")
    @ResponseBody
    public Map<String, Object> check(HttpServletRequest req) {
        guard(req);
        List<Map<String, Object>> items = new ArrayList<>();
        int java = Runtime.version().feature();
        items.add(item("Java 版本", "Java " + Runtime.version(), java >= 17, "需要 Java 17 或更高"));
        boolean writable = Files.isWritable(install.dataDir()) || !Files.exists(install.dataDir());
        items.add(item("数据目录", install.dataDir().toString(), writable, "没有写入权限，请检查目录权限"));
        long free = install.freeDiskBytes();
        items.add(item("剩余磁盘", free < 0 ? "未知" : gb(free), free < 0 || free > 1024L * 1024 * 1024, "剩余空间不足 1 GB，更新包多了会放不下"));
        long max = Runtime.getRuntime().maxMemory();
        items.add(item("可用内存", gb(max), max >= 256L * 1024 * 1024, "建议给 Java 至少 512 MB 内存（-Xmx512m）"));
        items.add(item("运行时长", (ManagementFactory.getRuntimeMXBean().getUptime() / 1000) + " 秒", true, null));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", items.stream().allMatch(i -> Boolean.TRUE.equals(i.get("ok")) || i.get("warn") == null));
        out.put("items", items);
        out.put("version", OtaPlatformApplication.version());
        return out;
    }

    @PostMapping("/db")
    @ResponseBody
    public InstallService.DbCheck db(@RequestBody InstallService.DbForm form, HttpServletRequest req) {
        guard(req);
        return install.testDatabase(form);
    }

    @PostMapping("/submit")
    @ResponseBody
    public Map<String, Object> submit(@RequestBody InstallService.Form form, HttpServletRequest req) {
        guard(req);
        install.submit(form, req.getServerName());
        if (req.getSession(false) != null) req.getSession(false).removeAttribute(InstallFilters.REOPEN);
        return Map.of("ok", true, "restarting", true, "boot", install.boot());
    }

    /** 重启过程中前端轮询：boot 变了、installed 为 true 就是重启好了。系统配置里恢复备份后等重启也用它 */
    @GetMapping("/status")
    @ResponseBody
    public Map<String, Object> status() {
        return Map.of("installed", install.installed(), "restarting", install.restarting(), "boot", install.boot());
    }

    private static Map<String, Object> item(String name, String value, boolean ok, String warn) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        m.put("ok", ok);
        m.put("warn", ok ? null : warn);
        return m;
    }

    private static String gb(long bytes) {
        return bytes >= 1024L * 1024 * 1024 ? String.format("%.1f GB", bytes / 1073741824.0) : (bytes / 1048576) + " MB";
    }
}
