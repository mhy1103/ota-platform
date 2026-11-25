package com.ota.platform.web;

import com.ota.platform.admin.AccessService;
import com.ota.platform.app.AppIcons;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.config.LoginGuard;
import com.ota.platform.settings.SettingService;
import com.ota.platform.share.ScreenShare;
import com.ota.platform.share.ScreenShareService;
import com.ota.platform.web.admin.AdminAppsController;
import com.ota.platform.web.admin.ScreenView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 分享出去的数据大屏（/s/口令）：不用登录，页面和分享的人在后台看到的一模一样（按 TA 现在的权限和外观取数），只是不能操作。
 * 设了访问密码的先输密码；过期、取消了、分享的人没权限了，显示分享已失效
 */
@Controller
public class ShareController {

    private final ScreenShareService shares;
    private final AccessService access;
    private final AppService apps;
    private final ScreenView view;
    private final Prefs prefs;
    private final SettingService settings;
    private final AppIcons icons;

    public ShareController(ScreenShareService shares, AccessService access, AppService apps, ScreenView view, Prefs prefs,
                           SettingService settings, AppIcons icons) {
        this.shares = shares;
        this.access = access;
        this.apps = apps;
        this.view = view;
        this.prefs = prefs;
        this.settings = settings;
        this.icons = icons;
    }

    @GetMapping("/s/{token}")
    public String open(@PathVariable String token, HttpServletRequest req, HttpServletResponse res, Model model) {
        ScreenShare s = shares.find(token).orElse(null);
        if (s == null) return gone(model, "gone", null);
        switch (shares.state(s)) {
            case REVOKED -> { return gone(model, "revoked", s); }
            case EXPIRED -> { return gone(model, "expired", s); }
            default -> { }
        }
        if (s.isLocked() && !shares.unlocked(s, req)) return lock(model, s);
        String problem = access.actAs(s.getCreatedBy(), me -> {
            if (me == null) return "owner";
            List<OtaApp> visible = access.visible(apps.all());
            OtaApp viewed = null;
            if (!s.isAll()) {
                viewed = visible.stream().filter(a -> a.getId().equals(s.getAppId())).findFirst().orElse(null);
                if (viewed == null) return "owner";
            } else if (visible.isEmpty()) {
                return "owner";
            }
            view.fill(model, me, visible, viewed != null ? viewed : visible.get(0), viewed);
            Map<String, String> look = prefs.lookOf(me.username());
            model.addAttribute("themeCss", look == null ? settings.themeCss() : settings.themeCss(settings.appearance(look)));
            return null;
        });
        if (problem != null) return gone(model, problem, s);
        model.addAttribute("scShared", true);
        model.addAttribute("scShareUntil", s.getExpiresAt());
        model.addAttribute("scShareTheme", s.getTheme() == null ? "" : s.getTheme());
        model.addAttribute("scPicks", List.of());
        boolean secure = secure(req), fresh = !shares.seen(s, req);
        res.addHeader(HttpHeaders.SET_COOKIE, shares.assetCookie(s, secure).toString());
        if (fresh) res.addHeader(HttpHeaders.SET_COOKIE, shares.seenCookie(s, secure).toString());
        shares.visited(s, fresh);
        return "admin/screen";
    }

    /** 分享页上的 App 图标（后台的图标地址要登录）：分享有效、设了密码的输过密码、分享的人现在还能看到这个 App，而且在分享的范围里 */
    @GetMapping("/s/{token}/icon/{appId}")
    public ResponseEntity<byte[]> icon(@PathVariable String token, @PathVariable Long appId, HttpServletRequest req) {
        ScreenShare s = shares.find(token).orElse(null);
        if (s == null || shares.state(s) != ScreenShareService.State.ACTIVE || (s.isLocked() && !shares.unlocked(s, req))) {
            return ResponseEntity.notFound().build();
        }
        Boolean ok = access.actAs(s.getCreatedBy(), me -> me != null && me.canSee(appId) && (s.isAll() || appId.equals(s.getAppId())));
        if (!Boolean.TRUE.equals(ok)) return ResponseEntity.notFound().build();
        return icons.get(appId).map(AdminAppsController::iconResponse).orElse(ResponseEntity.notFound().build());
    }

    /** 输访问密码 */
    @PostMapping("/s/{token}")
    public String unlock(@PathVariable String token, @RequestParam(defaultValue = "") String password,
                         HttpServletRequest req, HttpServletResponse res) {
        ScreenShare s = shares.find(token).orElse(null);
        if (s == null) return "redirect:/";
        String back = "redirect:/s/" + s.getToken();
        if (shares.state(s) != ScreenShareService.State.ACTIVE || !s.isLocked()) return back;
        return switch (shares.unlock(s, password, LoginGuard.ipOf(req))) {
            case OK -> {
                res.addHeader(HttpHeaders.SET_COOKIE, shares.unlockCookie(s, secure(req)).toString());
                yield back;
            }
            case LOCKED -> back + "?locked";
            default -> back + "?wrong";
        };
    }

    private String gone(Model model, String why, ScreenShare s) {
        model.addAttribute("shareState", why);
        model.addAttribute("shareUntil", s == null ? null : s.getExpiresAt());
        return "share";
    }

    private String lock(Model model, ScreenShare s) {
        model.addAttribute("shareState", "locked");
        model.addAttribute("shareName", s.isAll() ? "全部 App" : apps.all().stream().filter(a -> a.getId().equals(s.getAppId()))
                .map(OtaApp::getName).findFirst().orElse("数据大屏"));
        model.addAttribute("shareBy", s.getCreatedName() == null ? s.getCreatedBy() : s.getCreatedName());
        model.addAttribute("shareUntil", s.getExpiresAt());
        return "share";
    }

    private static boolean secure(HttpServletRequest req) {
        return req.isSecure() || "https".equalsIgnoreCase(req.getHeader("X-Forwarded-Proto"));
    }
}
