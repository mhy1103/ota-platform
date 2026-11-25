package com.ota.platform.web.admin;

import com.ota.platform.admin.AccessService;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AppContext;
import com.ota.platform.ota.ReleaseStatsService;
import com.ota.platform.signing.SigningInspector;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;

/**
 * 后台每个页面顶栏和侧栏都要的全局状态：能看到的 App、当前 App、它的 A/B 面和签名状态。
 * 当前 App 记在会话里，只能是当前管理员有权限的 App。
 */
@Component
public class AdminShell {

    public static final String SESSION_APP = "admin.app";

    private final AppService apps;
    private final SigningInspector signing;
    private final AccessService access;
    private final ReleaseViews views;
    private final ReleaseStatsService releaseStats;
    private final com.ota.platform.agent.AgentService agents;
    private final TodoService todos;

    public AdminShell(AppService apps, SigningInspector signing, AccessService access, ReleaseViews views, ReleaseStatsService releaseStats,
                      com.ota.platform.agent.AgentService agents, @org.springframework.context.annotation.Lazy TodoService todos) {
        this.agents = agents;
        this.todos = todos;
        this.apps = apps;
        this.signing = signing;
        this.access = access;
        this.views = views;
        this.releaseStats = releaseStats;
    }

    /** 当前管理员能看到的 App */
    public List<OtaApp> visible() {
        return access.visible(apps.all());
    }

    /** 当前 App：会话里选过的，否则第一个能看到的；一个都没有时返回 null */
    public OtaApp current(HttpSession session) {
        List<OtaApp> visible = visible();
        Object slug = session.getAttribute(SESSION_APP);
        OtaApp found = slug == null ? null : visible.stream().filter(a -> a.getSlug().equals(slug)).findFirst().orElse(null);
        if (found == null && !visible.isEmpty()) {
            found = visible.get(0);
            session.setAttribute(SESSION_APP, found.getSlug());
        }
        AppContext.set(found);
        return found;
    }

    public void use(HttpSession session, String slug) {
        visible().stream().filter(a -> a.getSlug().equals(slug)).findFirst()
                .ifPresent(a -> session.setAttribute(SESSION_APP, a.getSlug()));
    }

    public OtaApp populate(Model model, HttpSession session) {
        OtaApp app = current(session);
        model.addAttribute("apps", visible());
        model.addAttribute("app", app);
        model.addAttribute("face", app != null && app.isRedeemOpen() ? "B" : "A");
        model.addAttribute("switchedAt", app != null ? app.getSwitchedAt() : null);
        model.addAttribute("signing", app != null ? signing.inspect(app) : null);
        model.addAttribute("navAlerts", app != null ? alarmCount(app) : 0);
        com.ota.platform.admin.CurrentAdmin me = access.current();
        model.addAttribute("navRequests", me != null && me.canManageAgents() ? agents.pendingCount() : 0);
        model.addAttribute("todos", todos.all(app));
        return app;
    }

    /** 启动失败偏多的线上版本数，侧栏「版本发布」上显示，在哪个页面都能看到 */
    private int alarmCount(OtaApp app) {
        List<ReleaseViews.Live> live = views.live(app);
        List<String> ids = live.stream().filter(l -> l.getRelease() != null).map(l -> l.getRelease().getId()).distinct().toList();
        return ids.isEmpty() ? 0 : ReleaseViews.alarms(live, releaseStats.get(ids)).size();
    }
}
