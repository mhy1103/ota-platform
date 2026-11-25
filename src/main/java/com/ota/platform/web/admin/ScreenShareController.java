package com.ota.platform.web.admin;

import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.common.ApiException;
import com.ota.platform.security.TwoFactorService;
import com.ota.platform.share.ScreenShare;
import com.ota.platform.share.ScreenShareService;
import com.ota.platform.web.AdminUi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * 数据大屏右上角「分享」：生成不用登录就能看的链接（选有效期、可选访问密码），列出分享记录，取消分享。
 * 数据大屏页面上用脚本调，返回 JSON
 */
@RestController
@RequestMapping("/admin/screen/shares")
public class ScreenShareController {

    private final ScreenShareService shares;
    private final AccessService access;
    private final AdminShell shell;
    private final AppService apps;
    private final AuditService audit;
    private final AdminUi ui;

    public ScreenShareController(ScreenShareService shares, AccessService access, AdminShell shell, AppService apps,
                                 AuditService audit, AdminUi ui) {
        this.shares = shares;
        this.access = access;
        this.shell = shell;
        this.apps = apps;
        this.audit = audit;
        this.ui = ui;
    }

    /** 分享记录的一条 */
    public record Row(Long id, String path, String scope, String appName, String appSlug, Long appId, String appIcon,
                      String createdBy, String createdName, Long createdAt, Long expiresAt, boolean locked, String note,
                      String state, long views, Long lastViewedAt, Long revokedAt, boolean mine) {}

    @GetMapping
    public Map<String, Object> list() {
        CurrentAdmin me = access.current();
        if (me == null) return fail("登录已失效，请重新登录");
        Map<Long, OtaApp> byId = new HashMap<>();
        for (OtaApp a : apps.all()) byId.put(a.getId(), a);
        List<Row> rows = shares.list(me.username(), me.owner()).stream().map(s -> row(s, byId, me)).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("shares", rows);
        out.put("owner", me.owner());
        out.put("hours", ScreenShareService.HOURS);
        return out;
    }

    @PostMapping
    public Map<String, Object> create(@RequestParam(defaultValue = "app") String scope, @RequestParam(required = false) String app,
                                      @RequestParam(defaultValue = "24") int hours, @RequestParam(defaultValue = "") String password,
                                      @RequestParam(defaultValue = "") String note, @RequestParam(defaultValue = "") String theme,
                                      @RequestParam(defaultValue = "") String origin, HttpServletRequest req, HttpServletResponse res) {
        CurrentAdmin me = access.current();
        if (me == null) return fail("登录已失效，请重新登录");
        List<OtaApp> visible = shell.visible();
        boolean all = "all".equals(scope);
        OtaApp viewed = null;
        if (all) {
            if (visible.size() < 2) return fail("你只能看到一个 App，分享这个 App 的大屏就行");
        } else {
            String slug = app == null ? "" : app.trim();
            viewed = visible.stream().filter(a -> a.getSlug().equals(slug)).findFirst().orElse(null);
            if (viewed == null) return fail("没有这个 App，或者你没有权限看它");
        }
        try {
            ScreenShare s = shares.create(me.username(), me.name(), all, viewed == null ? null : viewed.getId(), hours, password, note, theme);
            audit.log(viewed == null ? null : viewed.getId(), "SCREEN_SHARE_CREATE", "分享数据大屏（" + (all ? "全部 App" : viewed.getName()) + "）："
                    + (s.getExpiresAt() == null ? "长期有效" : "有效期至 " + ui.full(s.getExpiresAt())) + (s.isLocked() ? " · 带访问密码" : "")
                    + (s.getNote() == null ? "" : " · " + s.getNote()));
            Map<Long, OtaApp> byId = new HashMap<>();
            if (viewed != null) byId.put(viewed.getId(), viewed);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("share", row(s, byId, me));
            String url = base(origin, req) + "/s/" + s.getToken();
            out.put("url", url);
            out.put("qr", TwoFactorService.svg(url));
            return out;
        } catch (ApiException e) {
            res.setStatus(e.getStatus().value());
            return fail(e.getMessage());
        }
    }

    @PostMapping("/{id}/revoke")
    public Map<String, Object> revoke(@PathVariable Long id, HttpServletResponse res) {
        CurrentAdmin me = access.current();
        if (me == null) return fail("登录已失效，请重新登录");
        try {
            boolean was = shares.list(me.username(), me.owner()).stream().anyMatch(x -> x.getId().equals(id) && x.getRevokedAt() == null);
            ScreenShare s = shares.revoke(id, me.username(), me.owner());
            Map<Long, OtaApp> byId = new HashMap<>();
            for (OtaApp a : apps.all()) byId.put(a.getId(), a);
            if (was) {
                OtaApp a = s.getAppId() == null ? null : byId.get(s.getAppId());
                audit.log(s.getAppId(), "SCREEN_SHARE_REVOKE", "取消数据大屏分享（" + (s.isAll() ? "全部 App" : a == null ? "已删除的 App" : a.getName()) + "，"
                        + (s.getCreatedName() == null ? s.getCreatedBy() : s.getCreatedName()) + " 分享的）");
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("share", row(s, byId, me));
            return out;
        } catch (ApiException e) {
            res.setStatus(e.getStatus().value());
            return fail(e.getMessage());
        }
    }

    private Row row(ScreenShare s, Map<Long, OtaApp> byId, CurrentAdmin me) {
        OtaApp a = s.getAppId() == null ? null : byId.get(s.getAppId());
        return new Row(s.getId(), "/s/" + s.getToken(), s.getScope(), s.isAll() ? null : a == null ? "已删除的 App" : a.getName(),
                a == null ? null : a.getSlug(), a == null ? null : a.getId(), a == null ? null : a.getIconVersion(),
                s.getCreatedBy(), s.getCreatedName(), ms(s.getCreatedAt()), ms(s.getExpiresAt()), s.isLocked(), s.getNote(),
                shares.state(s).name().toLowerCase(Locale.ROOT), s.getViews(), ms(s.getLastViewedAt()), ms(s.getRevokedAt()),
                s.getCreatedBy().equalsIgnoreCase(me.username()));
    }

    private static Long ms(LocalDateTime t) {
        return t == null ? null : t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    /** 链接用页面现在的地址（和地址栏里一样）；传来的不是这个站点的就按请求自己拼 */
    private static String base(String origin, HttpServletRequest req) {
        String host = Optional.ofNullable(req.getHeader("X-Forwarded-Host")).orElse(req.getHeader("Host"));
        String h = host == null ? "" : host.split(",")[0].trim();
        try {
            URI u = URI.create(origin.trim());
            String have = u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
            if (("http".equals(u.getScheme()) || "https".equals(u.getScheme())) && !h.isEmpty()
                    && (have.equalsIgnoreCase(h) || u.getHost().equalsIgnoreCase(h.replaceAll(":\\d+$", "")))) {
                return u.getScheme() + "://" + have;
            }
        } catch (Exception ignored) {
            // 按请求拼
        }
        String proto = Optional.ofNullable(req.getHeader("X-Forwarded-Proto")).orElse(req.getScheme());
        return proto.split(",")[0].trim() + "://" + (h.isEmpty() ? req.getServerName() + ":" + req.getServerPort() : h);
    }

    private static Map<String, Object> fail(String message) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", false);
        m.put("message", message);
        return m;
    }
}
