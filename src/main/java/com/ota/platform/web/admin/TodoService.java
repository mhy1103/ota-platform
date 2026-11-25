package com.ota.platform.web.admin;

import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AppPerm;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.agent.AgentService;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.OperationLogRepo;
import com.ota.platform.install.InstallService;
import com.ota.platform.ota.ReleaseStat;
import com.ota.platform.ota.ReleaseStatsService;
import com.ota.platform.signing.SigningInspector;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 待处理：要有人去处理的事。App 的事是启动失败偏多、签名、备份，不属于哪个 App 的是口令申请、接口对外地址。
 * 顶栏的铃铛和全部 App 的数据大屏列能看到的所有 App 的；当前概览和看一个 App 的数据大屏只列这个 App 的，加上不属于哪个 App 的。
 * 每个页面都要算，结果按管理员缓存几秒；任何提交（POST）之后作废，处理完马上看得到变化
 */
@Component
public class TodoService {

    private static final long TTL_MS = 10_000;
    private static final List<String> ORDER = List.of("error", "warn", "info");

    /** kind：requests / alarm / signing / backup / port；level：error 要马上处理、warn 该处理了、info 提醒；app 是 App 名称（不属于某个 App 时为空） */
    public record Todo(String kind, String level, String title, String detail, String href, String action, String app, Long appId) {}

    private record Cached(long at, long version, List<Todo> list) {}

    private final AppService apps;
    private final AccessService access;
    private final SigningInspector signing;
    private final ReleaseViews views;
    private final ReleaseStatsService releaseStats;
    private final AgentService agents;
    private final InstallService install;
    private final OperationLogRepo logs;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private final AtomicLong version = new AtomicLong();

    public TodoService(AppService apps, AccessService access, SigningInspector signing, ReleaseViews views, ReleaseStatsService releaseStats,
                       AgentService agents, InstallService install, OperationLogRepo logs) {
        this.apps = apps;
        this.access = access;
        this.signing = signing;
        this.views = views;
        this.releaseStats = releaseStats;
        this.agents = agents;
        this.install = install;
        this.logs = logs;
    }

    /** 有数据变了（提交表单、调接口）：缓存作废 */
    public void changed() {
        version.incrementAndGet();
    }

    /** 正在看的 App 的待处理（当前概览） */
    public List<Todo> list(OtaApp current) {
        return of(current, current);
    }

    /** app 的待处理；current 是会话里的当前 App，链接到别的 App 的页面时先切换过去 */
    public List<Todo> of(OtaApp app, OtaApp current) {
        return cached("app:" + (app == null ? "" : app.getSlug()), current, app == null ? List.of() : List.of(app));
    }

    /** 能看到的所有 App 的待处理（顶栏铃铛、全部 App 的数据大屏） */
    public List<Todo> all(OtaApp current) {
        return cached("all", current, null);
    }

    private List<Todo> cached(String scope, OtaApp current, List<OtaApp> only) {
        CurrentAdmin me = access.current();
        if (me == null) return List.of();
        String key = me.username() + "|" + scope + "|" + (current == null ? "" : current.getSlug());
        long v = version.get();
        Cached c = cache.get(key);
        if (c != null && c.version() == v && System.currentTimeMillis() - c.at() < TTL_MS) return c.list();
        List<OtaApp> scopeApps = only != null ? only.stream().filter(a -> me.canSee(a.getId())).toList() : access.visible(apps.all());
        List<Todo> list = build(me, current, scopeApps);
        if (cache.size() > 2000) cache.clear();
        cache.put(key, new Cached(System.currentTimeMillis(), v, list));
        return list;
    }

    private List<Todo> build(CurrentAdmin me, OtaApp current, List<OtaApp> scope) {
        List<Todo> out = new ArrayList<>();
        if (me.canManageAgents()) {
            long n = agents.pendingCount();
            if (n > 0) out.add(new Todo("requests", "warn", n + " 条口令申请待审核", agents.pendingAgentsText(), "/admin/agents#requests", "去审核", null, null));
        }
        for (OtaApp a : scope) {
            List<ReleaseViews.Live> lives = views.live(a);
            Map<String, ReleaseStat> st = releaseStats.get(lives.stream().filter(l -> l.getRelease() != null).map(l -> l.getRelease().getId()).distinct().toList());
            for (ReleaseViews.Alarm x : ReleaseViews.alarms(lives, st)) {
                out.add(new Todo("alarm", "error", x.channels() + " 的 " + x.release().shortId() + " 启动失败偏多",
                        "推送 " + x.stat().getOffered() + " 台，失败 " + x.stat().getFailed() + " 台（" + x.stat().getFailRate() + "%）",
                        link(a, current, "/admin/releases"), "去处理", a.getName(), a.getId()));
            }
            SigningInspector.Status sig = signing.inspect(a);
            if (!"ok".equals(sig.getLevel())) {
                out.add(new Todo("signing", "error".equals(sig.getLevel()) ? "error" : "warn", sig.getHeadline(), "签名不对，App 会拒绝更新",
                        link(a, current, "/admin/signing"), "查看", a.getName(), a.getId()));
            }
            if (me.can(a.getId(), AppPerm.SIGNING) && !logs.existsByActionAndDetailStartingWith("APP_BACKUP", a.getSlug() + " ")) {
                out.add(new Todo("backup", "info", "还没下载过备份", "签名密钥丢了，装好的 App 就收不到更新", link(a, current, "/admin/app#backup"), "下载备份", a.getName(), a.getId()));
            }
        }
        if (me.owner()) {
            String fix = install.publicUrlFix();
            if (fix != null) out.add(new Todo("port", "warn", "接口对外地址少了端口", "应该是 " + fix, "/admin/settings#install", "去改", null, null));
        }
        out.sort(Comparator.comparingInt(t -> ORDER.indexOf(t.level())));
        return List.copyOf(out);
    }

    /** 别的 App 的页面要先切换 */
    private static String link(OtaApp a, OtaApp current, String path) {
        if (current != null && current.getId().equals(a.getId())) return path;
        return "/admin/use?app=" + a.getSlug() + "&back=" + URLEncoder.encode(path, StandardCharsets.UTF_8);
    }
}
