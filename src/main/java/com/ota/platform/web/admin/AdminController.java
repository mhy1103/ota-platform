package com.ota.platform.web.admin;

import com.ota.platform.web.Csv;
import com.ota.platform.web.agent.AgentPortal;
import com.ota.platform.web.AdminUi;
import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AdminUserService;
import com.ota.platform.admin.AppPerm;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.agent.*;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.audit.OperationLog;
import com.ota.platform.audit.OperationLogRepo;
import com.ota.platform.codes.*;
import com.ota.platform.common.ApiException;
import com.ota.platform.ota.LaunchErrorService;
import com.ota.platform.ota.OtaEvents;
import com.ota.platform.ota.ReleaseStat;
import com.ota.platform.ota.ReleaseStatsService;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseRepository;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final int PAGE_SIZE = 50;
    /** 导出 CSV 的行数上限 */
    private static final int EXPORT_LIMIT = 100_000;
    private static final List<String> CODE_STATUSES = List.of("all", "usable", "disabled", "expired", "exhausted");
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private final AppService apps;
    private final ActivationCodeRepo codeRepo;
    private final ActivationRecordRepo recordRepo;
    private final OperationLogRepo logRepo;
    private final AgentRepo agentRepo;
    private final AgentPortal portal;
    private final com.ota.platform.agent.AgentService agentService;
    private final AdminShell shell;
    private final AdminUi ui;
    private final AuditService audit;
    private final CodeAdminService codeAdmin;
    private final ReleaseViews releaseViews;
    private final DeviceViews deviceViews;
    private final ReleaseRepository releaseRepo;
    private final OtaEvents events;
    private final ReleaseStatsService releaseStats;
    private final LaunchErrorService launchErrors;
    private final AccessService access;
    private final AdminUserService adminUsers;
    private final TodoService todos;
    private final com.ota.platform.stats.StatsService stats;
    private final com.ota.platform.schedule.ScheduleService schedules;
    private final OverviewBoard board;

    public AdminController(AppService apps, ActivationCodeRepo codeRepo, ActivationRecordRepo recordRepo, OperationLogRepo logRepo,
                           AgentRepo agentRepo, AgentPortal portal, AdminShell shell, AdminUi ui, AuditService audit,
                           CodeAdminService codeAdmin, ReleaseViews releaseViews, DeviceViews deviceViews,
                           ReleaseRepository releaseRepo, OtaEvents events, ReleaseStatsService releaseStats,
                           LaunchErrorService launchErrors, AccessService access, AdminUserService adminUsers, TodoService todos,
                           com.ota.platform.agent.AgentService agentService, com.ota.platform.stats.StatsService stats,
                           com.ota.platform.schedule.ScheduleService schedules, OverviewBoard board) {
        this.schedules = schedules;
        this.board = board;
        this.agentService = agentService;
        this.stats = stats;
        this.todos = todos;
        this.apps = apps;
        this.codeRepo = codeRepo;
        this.recordRepo = recordRepo;
        this.logRepo = logRepo;
        this.agentRepo = agentRepo;
        this.portal = portal;
        this.shell = shell;
        this.ui = ui;
        this.audit = audit;
        this.codeAdmin = codeAdmin;
        this.releaseViews = releaseViews;
        this.deviceViews = deviceViews;
        this.releaseRepo = releaseRepo;
        this.events = events;
        this.releaseStats = releaseStats;
        this.launchErrors = launchErrors;
        this.access = access;
        this.adminUsers = adminUsers;
    }

    /** 概览的接入步骤 */
    public record Step(String title, String text, String link, String action, boolean done) {}

    /** 概览趋势图的一天：label 是横轴上显示的（天数多时隔几天显示一个），date 是完整日期，px 是柱子高度 */
    /** ios / android：这天两个平台各多少台，iosPct：iOS 占有平台记录的设备的百分比（没有平台记录时算 100，整根一个颜色） */
    public record Day(String label, String date, long value, int px, boolean today, long ios, long android, int iosPct) {
        public Day(String label, String date, long value, int px, boolean today) {
            this(label, date, value, px, today, 0, 0, 100);
        }
    }

    /** 和上一段时间比的变化：dir 是 up / down / flat，text 是显示的文字（12%、持平、新增） */
    public record Delta(String dir, String text) {}

    /** 今天每个小时的有效激活，px 是柱子高度 */
    /** 今天每小时的有效激活；later 是还没到的钟点 */
    public record Hour(int hour, long value, int px, boolean now, boolean later) {}

    /** 概览趋势图可选的天数 */
    public static final List<Integer> TREND_RANGES = List.of(7, 14, 30);

    // ==================== 页面 ====================

    @GetMapping
    public String overview(@RequestParam(defaultValue = "14") int range, Model model, HttpSession session) {
        OtaApp app = shell.populate(model, session);
        if (app == null) return "redirect:/admin/apps";
        long confirmed = recordRepo.countByAppIdAndConfirmedTrue(app.getId());
        long codeTotal = codeRepo.count();
        LocalDateTime today = ui.startOfToday();
        List<ReleaseViews.Live> live = releaseViews.live(app);
        DeviceViews.Distribution dist = deviceViews.distribution(app);
        model.addAttribute("live", live);
        model.addAttribute("activeDevices", dist.active());
        model.addAttribute("dist", dist);
        model.addAttribute("confirmedTotal", confirmed);
        model.addAttribute("pendingTotal", recordRepo.countPendingByApp(app.getId()));
        model.addAttribute("confirmedToday", recordRepo.countByAppIdAndConfirmedTrueAndConfirmedAtGreaterThanEqual(app.getId(), today));
        model.addAttribute("submittedToday", recordRepo.countByAppIdAndActivatedAtGreaterThanEqual(app.getId(), today));
        model.addAttribute("codeTotal", codeTotal);
        model.addAttribute("codeUsable", codeRepo.count(codeSpec("", "usable", "")));
        model.addAttribute("recentRecords", recordRepo.findTop8ByAppIdOrderByActivatedAtDesc(app.getId()));
        Specification<OperationLog> appOps = (root, cq, cb) -> cb.and(cb.equal(root.get("appId"), app.getId()),
                cb.not(root.get("action").in(AuditService.DEVICE_EVENTS)));
        model.addAttribute("recentOps", logRepo.findAll(appOps, PageRequest.of(0, 8, Sort.by(Sort.Direction.DESC, "createdAt"))).getContent());
        CurrentAdmin viewer = access.current();
        boolean seesAgents = viewer != null && viewer.canManageAgents();
        List<AgentPortal.AgentRow> ranking = seesAgents ? new ArrayList<>(portal.adminRows()) : new ArrayList<>();
        ranking.sort(Comparator.comparingLong(AgentPortal.AgentRow::getToday).reversed()
                .thenComparing(Comparator.comparingLong(AgentPortal.AgentRow::getTotal).reversed()));
        model.addAttribute("agentCount", ranking.size());
        model.addAttribute("agentsEnabled", ranking.stream().filter(r -> Boolean.TRUE.equals(r.getAgent().getEnabled())).count());
        model.addAttribute("agentsActiveToday", ranking.stream().filter(r -> r.getToday() > 0).count());
        model.addAttribute("pendingRequests", seesAgents ? agentService.pendingCount() : 0);
        model.addAttribute("codeExhausted", codeRepo.count(codeSpec("", "exhausted", "")));
        model.addAttribute("codeDisabled", codeRepo.count(codeSpec("", "disabled", "")));
        model.addAttribute("codeExpired", codeRepo.count(codeSpec("", "expired", "")));
        LocalDateTime now = LocalDateTime.now();
        model.addAttribute("codeExpiring", codeRepo.count((root, cq, cb) -> cb.and(cb.isTrue(root.get("enabled")),
                cb.between(root.get("expiresAt"), now, now.plusDays(7)))));
        model.addAttribute("codesUsedToday", recordRepo.countCodesUsedSince(app.getId(), today));
        model.addAttribute("ranking", ranking.subList(0, Math.min(5, ranking.size())));

        boolean released = live.stream().anyMatch(l -> l.getRelease() != null && !l.isRollbackToEmbedded());
        List<Step> steps = List.of(
                new Step("接入 App", "下载证书放进 App，按「App 设置」里的配置改 app.json，重新打包", "/admin/app", "接入配置", dist.active() > 0),
                new Step("发布第一个版本", "上传 expo export 导出的包，或者用命令行 ota publish", "/admin/releases#upload", "上传更新包",
                        released || releaseRepo.countByAppId(app.getId()) > 0),
                new Step("新建口令", "新建或批量生成口令，分配给代理", "/admin/codes#new", "新建口令", codeTotal > 0),
                new Step("下载备份", "签名密钥和口令接口密钥都在备份里，丢了能原样恢复", "/admin/app#backup", "下载备份",
                        logRepo.existsByActionAndDetailStartingWith("APP_BACKUP", app.getSlug() + " ")));
        model.addAttribute("steps", steps);
        model.addAttribute("stepsDone", steps.stream().filter(Step::done).count());
        trend(model, app, TREND_RANGES.contains(range) ? range : 14);
        devices(model, app.getId(), stats, 14);
        model.addAttribute("modePlans", schedules.pending(app.getId()).stream()
                .filter(t -> com.ota.platform.schedule.ScheduledTask.MODE.equals(t.getKind())).toList());

        Map<String, ReleaseStat> st = releaseStats.get(releaseRepo.findByAppIdOrderByCreatedAtDesc(app.getId()).stream().map(Release::getId).toList());
        ReleaseViews.Live latest = live.stream().filter(l -> l.getRelease() != null && !l.isRollbackToEmbedded())
                .max(Comparator.comparing(ReleaseViews.Live::getUpdatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))).orElse(null);
        model.addAttribute("latest", latest);
        model.addAttribute("latestChannels", latest == null ? "" : String.join("、", live.stream()
                .filter(l -> l.getRelease() != null && !l.isRollbackToEmbedded() && l.getRelease().getId().equals(latest.getRelease().getId()))
                .map(ReleaseViews.Live::getChannel).toList()));
        Map<String, String> names = new HashMap<>();
        for (com.ota.platform.admin.AdminUser u : adminUsers.all()) names.put(u.getUsername(), u.getName());
        model.addAttribute("operatorNames", names);
        model.addAttribute("latestStat", latest == null ? null : st.get(latest.getRelease().getId()));
        long served = 0, saved = 0, patchDl = 0, fullDl = 0;
        for (ReleaseStat x : st.values()) {
            served += x.getBytesServed();
            saved += x.getBytesSaved();
            patchDl += x.getPatchDownloads();
            fullDl += x.getFullDownloads() + x.getCdnDownloads();
        }
        model.addAttribute("bytesServed", served);
        model.addAttribute("bytesSaved", saved);
        model.addAttribute("savedPct", served + saved > 0 ? Math.round(saved * 100.0 / (served + saved)) : 0);
        model.addAttribute("patchDownloads", patchDl);
        model.addAttribute("fullDownloads", fullDl);
        model.addAttribute("errors24h", launchErrors.countSince(app.getId(), java.time.Duration.ofDays(1)));
        model.addAttribute("alerts", ReleaseViews.alarms(live, st));
        // 概览上已经有这个 App 的启动失败告警条和「开始使用」清单，待处理里就不重复了
        CurrentAdmin me = access.current();
        boolean stepsShown = steps.stream().anyMatch(s -> !s.done()) && me != null && me.can(app.getId(), AppPerm.SETTINGS);
        model.addAttribute("ovTodos", todos.list(app).stream()
                .filter(t -> !app.getId().equals(t.appId()) || !("alarm".equals(t.kind()) || stepsShown && "backup".equals(t.kind())))
                .toList());
        if (viewer != null) board.fill(model, app, viewer);
        return "admin/overview";
    }

    /**
     * 概览上的激活数据：近 range 天每天的有效激活（和再往前同样天数比）、今天比昨天、近 7 天比前 7 天、近 14 天的迷你趋势线。
     * 按天数取一次确认时间在本地时区里分到每天。
     */
    private void trend(Model model, OtaApp app, int range) {
        LocalDate end = ui.today();
        int span = Math.max(range * 2, 14);
        LocalDate first = end.minusDays(span - 1);
        ZoneId zone = ZoneId.of(ui.zoneId());
        LocalDateTime since = first.atStartOfDay(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        Map<LocalDate, Long> perDay = new HashMap<>();
        long[] hourly = new long[24];
        for (LocalDateTime t : recordRepo.confirmedTimesSince(app.getId(), since)) {
            if (t == null) continue;
            LocalDate d = ui.localDate(t);
            perDay.merge(d, 1L, Long::sum);
            if (d.equals(end)) hourly[t.atZone(ZoneId.systemDefault()).withZoneSameInstant(zone).getHour()]++;
        }
        int nowHour = java.time.ZonedDateTime.now(zone).getHour();
        long hourMax = java.util.Arrays.stream(hourly).max().orElse(0);
        List<Hour> hours = new ArrayList<>();
        for (int h = 0; h < 24; h++) {
            hours.add(new Hour(h, hourly[h], hourMax == 0 ? 2 : (int) Math.max(2, Math.round(hourly[h] * 52.0 / hourMax)), h == nowHour, h > nowHour));
        }
        model.addAttribute("hours", hours);
        int peak = 0;
        for (int h = 1; h < 24; h++) if (hourly[h] > hourly[peak]) peak = h;
        model.addAttribute("peakHour", hourMax == 0 ? null : peak);
        long[] daily = new long[span];
        for (int i = 0; i < span; i++) daily[i] = perDay.getOrDefault(first.plusDays(i), 0L);

        long max = 0;
        for (int i = span - range; i < span; i++) max = Math.max(max, daily[i]);
        List<Day> days = new ArrayList<>();
        DateTimeFormatter md = DateTimeFormatter.ofPattern("MM-dd");
        int every = range <= 14 ? 1 : 5;
        for (int i = span - range, k = 0; i < span; i++, k++) {
            LocalDate d = first.plusDays(i);
            boolean today = d.equals(end);
            long v = daily[i];
            int px = max == 0 ? 2 : (int) Math.max(2, Math.round(v * 140.0 / max));
            String label = today ? "今天" : (range - 1 - k) % every == 0 ? md.format(d) : "";
            days.add(new Day(label, today ? "今天" : md.format(d), v, px, today));
        }
        model.addAttribute("trend", days);
        model.addAttribute("range", range);
        model.addAttribute("ranges", TREND_RANGES);
        long cur = sum(daily, span - range, span), prev = sum(daily, span - 2 * range, span - range);
        model.addAttribute("trendTotal", cur);
        model.addAttribute("trendDelta", delta(cur, prev));
        model.addAttribute("yesterdayCount", daily[span - 2]);
        model.addAttribute("todayDelta", delta(daily[span - 1], daily[span - 2]));
        long week = sum(daily, span - 7, span);
        model.addAttribute("weekCount", week);
        model.addAttribute("weekDelta", delta(week, sum(daily, span - 14, span - 7)));
        long[] spark = java.util.Arrays.copyOfRange(daily, span - 14, span);
        model.addAttribute("sparkLine", AdminUi.sparkPath(spark, 120, 34, false));
        model.addAttribute("sparkArea", AdminUi.sparkPath(spark, 120, 34, true));
    }

    /**
     * 设备看板：今天的活跃设备和新设备（比昨天）、近 7 / 30 天活跃、今天的平台占比、近 days 天每天的活跃设备。
     * 设备统计页也用（30 天）。
     */
    static void devices(Model model, Long appId, com.ota.platform.stats.StatsService stats, int days) {
        List<com.ota.platform.stats.StatsService.Day> list = stats.days(appId, days);
        com.ota.platform.stats.StatsService.Day today = list.get(list.size() - 1), yesterday = list.get(list.size() - 2);
        boolean known = stats.hasHistory(appId, yesterday.day());
        model.addAttribute("devToday", today);
        model.addAttribute("devYesterday", known ? yesterday : null);
        model.addAttribute("devDelta", known ? delta(today.active(), yesterday.active()) : null);
        model.addAttribute("devNewDelta", known ? delta(today.fresh(), yesterday.fresh()) : null);
        model.addAttribute("devWeek", stats.activeSince(appId, java.time.Duration.ofDays(7)));
        model.addAttribute("devMonth", stats.activeSince(appId, java.time.Duration.ofDays(30)));
        long plat = today.ios() + today.android();
        model.addAttribute("devIosPct", com.ota.platform.common.Percent.of(today.ios(), plat));
        long max = list.stream().mapToLong(com.ota.platform.stats.StatsService.Day::active).max().orElse(0);
        DateTimeFormatter md = DateTimeFormatter.ofPattern("MM-dd");
        java.time.LocalDate since = stats.firstRecord(appId);
        List<Day> bars = new ArrayList<>();
        long sum7 = 0;
        int n7 = 0, gap = 0;
        for (int i = 0; i < list.size(); i++) {
            com.ota.platform.stats.StatsService.Day d = list.get(i);
            boolean isToday = i == list.size() - 1;
            int every = days <= 14 ? 2 : 5;
            String label = isToday ? "今天" : (list.size() - 1 - i) % every == 0 ? md.format(d.day()) : "";
            if (!isToday && d.day().isBefore(since)) {
                gap++;
                bars.add(new Day(label, md.format(d.day()), 0, 2, false));
                continue;
            }
            int px = max == 0 ? 2 : (int) Math.max(2, Math.round(d.active() * 56.0 / max));
            long plats = d.ios() + d.android();
            int iosPct = plats == 0 ? 100 : com.ota.platform.common.Percent.of(d.ios(), plats);
            bars.add(new Day(label, isToday ? "今天" : md.format(d.day()), d.active(), px, isToday, d.ios(), d.android(), iosPct));
            if (!isToday && i >= list.size() - 8) {
                sum7 += d.active();
                n7++;
            }
        }
        model.addAttribute("devDays", bars);
        model.addAttribute("devGap", gap);
        model.addAttribute("devSince", gap > 0 ? md.format(since) : null);
        model.addAttribute("devAvg7", n7 == 0 ? null : Math.round(sum7 * 1.0 / n7));
        model.addAttribute("devPeakDay", bars.stream().filter(b -> b.value() == max && max > 0).map(Day::date).findFirst().orElse(null));
    }

    private static long sum(long[] a, int from, int to) {
        long s = 0;
        for (int i = Math.max(0, from); i < to; i++) s += a[i];
        return s;
    }

    /** 和之前比的变化，百分比四舍五入；之前和现在都是 0 时不显示 */
    public static Delta delta(long cur, long prev) {
        if (prev == 0) return cur == 0 ? null : new Delta("up", "新增");
        long p = Math.round((cur - prev) * 100.0 / prev);
        return new Delta(p > 0 ? "up" : p < 0 ? "down" : "flat", p == 0 ? "持平" : Math.abs(p) + "%");
    }

    /** 切换当前 App，回到原来的页面 */
    @GetMapping("/use")
    public String use(@RequestParam String app, @RequestParam(defaultValue = "/admin") String back, HttpSession session) {
        shell.use(session, app);
        return "redirect:" + (back.matches("/admin(/[a-z-]*)?(#[a-z-]+)?") ? back : "/admin");
    }

    @GetMapping("/codes")
    public String codes(@RequestParam(defaultValue = "") String agent,
                        @RequestParam(defaultValue = "all") String status,
                        @RequestParam(defaultValue = "") String q,
                        @RequestParam(defaultValue = "1") int page,
                        Model model, HttpSession session) {
        shell.populate(model, session);
        String st = CODE_STATUSES.contains(status) ? status : "all";
        String query = q.trim();
        Map<String, Long> counts = new LinkedHashMap<>();
        for (String s : CODE_STATUSES) counts.put(s, codeRepo.count(codeSpec(agent, s, query)));
        Page<ActivationCode> codes = codeRepo.findAll(codeSpec(agent, st, query),
                PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id"))));
        List<Long> ids = codes.map(ActivationCode::getId).toList();
        Map<Long, Long> pending = new HashMap<>();
        Map<Long, LocalDateTime> lastUsed = new HashMap<>();
        if (!ids.isEmpty()) {
            for (Object[] row : recordRepo.countPendingByCodeIds(ids)) pending.put((Long) row[0], (Long) row[1]);
            for (Object[] row : recordRepo.lastActivityByCodeIds(ids)) lastUsed.put((Long) row[0], (LocalDateTime) row[1]);
        }
        model.addAttribute("codes", codes);
        model.addAttribute("pending", pending);
        model.addAttribute("lastUsed", lastUsed);
        model.addAttribute("counts", counts);
        model.addAttribute("codeTotal", codeRepo.count());
        model.addAttribute("codeUsable", codeRepo.count(codeSpec("", "usable", "")));
        model.addAttribute("agent", agent);
        model.addAttribute("status", st);
        model.addAttribute("q", query);
        model.addAttribute("filtered", !agent.isEmpty() || !"all".equals(st) || !query.isEmpty());
        model.addAttribute("back", ui.query("agent", agent, "status", st, "q", query, "page", codes.getNumber() + 1));
        model.addAttribute("agents", agentRepo.findByParentIdIsNullOrderByCreatedAtDesc());
        model.addAttribute("agentNames", agentNames());
        model.addAttribute("channels", apps.channelNames());
        return "admin/codes";
    }

    /** 激活记录的时间范围：[from, to)，服务器时区；null 表示不限 */
    record Window(LocalDateTime from, LocalDateTime to) {
        static final Window ALL = new Window(null, null);
    }

    /** range：all / today / yesterday / 7d / 30d / custom（用 from、to 两个日期，含当天） */
    private Window window(String range, String from, String to) {
        LocalDate today = ui.today();
        return switch (range == null ? "all" : range) {
            case "today" -> new Window(ui.startOf(today), null);
            case "yesterday" -> new Window(ui.startOf(today.minusDays(1)), ui.startOf(today));
            case "7d" -> new Window(ui.startOf(today.minusDays(6)), null);
            case "30d" -> new Window(ui.startOf(today.minusDays(29)), null);
            case "custom" -> {
                LocalDate f = parseDate(from);
                LocalDate t = parseDate(to);
                yield new Window(f == null ? null : ui.startOf(f), t == null ? null : ui.startOf(t.plusDays(1)));
            }
            default -> Window.ALL;
        };
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/activations")
    public String activations(@RequestParam(defaultValue = "all") String status,
                              @RequestParam(defaultValue = "") String code,
                              @RequestParam(defaultValue = "") String agent,
                              @RequestParam(name = "app", defaultValue = "") String appFilter,
                              @RequestParam(defaultValue = "") String q,
                              @RequestParam(defaultValue = "all") String range,
                              @RequestParam(defaultValue = "") String from,
                              @RequestParam(defaultValue = "") String to,
                              @RequestParam(defaultValue = "1") int page,
                              Model model, HttpSession session) {
        shell.populate(model, session);
        String codeFilter = code.trim();
        String query = q.trim();
        Long onlyApp = appFilter.matches("\\d{1,18}") ? Long.valueOf(appFilter) : null;
        Collection<Long> allowed = recordApps();
        Window w = window(range, from, to);
        PageRequest pr = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "activatedAt"));
        Page<ActivationRecord> records = recordRepo.findAll(recordSpec(allowed, onlyApp, status, codeFilter, agent, query, w), pr);
        boolean filtered = !"all".equals(status) || !codeFilter.isEmpty() || !agent.isEmpty() || onlyApp != null || !query.isEmpty() || !"all".equals(range);
        if (filtered) {
            long matched = recordRepo.count(recordSpec(allowed, onlyApp, "all", codeFilter, agent, query, w));
            long matchedConfirmed = recordRepo.count(recordSpec(allowed, onlyApp, "confirmed", codeFilter, agent, query, w));
            long matchedFast = recordRepo.count(recordSpec(allowed, onlyApp, "fast", codeFilter, agent, query, w));
            long matchedDup = recordRepo.count(recordSpec(allowed, onlyApp, "dup", codeFilter, agent, query, w));
            model.addAttribute("matched", matched);
            model.addAttribute("matchedConfirmed", matchedConfirmed);
            model.addAttribute("matchedPending", matched - matchedConfirmed - matchedFast - matchedDup);
            model.addAttribute("matchedFast", matchedFast);
            model.addAttribute("matchedDup", matchedDup);
        }

        long confirmed = allowed == null ? recordRepo.countByConfirmedTrue() : recordRepo.count(recordSpec(allowed, null, "confirmed", "", "", "", Window.ALL));
        long all = allowed == null ? recordRepo.count() : recordRepo.count(recordSpec(allowed, null, "all", "", "", "", Window.ALL));
        long fast = recordRepo.count(recordSpec(allowed, null, "fast", "", "", "", Window.ALL));
        long dup = recordRepo.count(recordSpec(allowed, null, "dup", "", "", "", Window.ALL));
        model.addAttribute("records", records);
        model.addAttribute("status", status);
        model.addAttribute("code", codeFilter);
        model.addAttribute("agent", agent);
        model.addAttribute("appFilter", onlyApp == null ? "" : String.valueOf(onlyApp));
        model.addAttribute("q", query);
        model.addAttribute("range", range);
        model.addAttribute("from", "custom".equals(range) ? from : "");
        model.addAttribute("to", "custom".equals(range) ? to : "");
        model.addAttribute("filtered", filtered);
        model.addAttribute("agents", agentRepo.findByParentIdIsNullOrderByCreatedAtDesc());
        model.addAttribute("recordApps", shell.visible());
        model.addAttribute("appNames", appNames());
        model.addAttribute("codeAgent", codeAgents(records.getContent()));
        model.addAttribute("confirmedTotal", confirmed);
        model.addAttribute("pendingTotal", all - confirmed - fast - dup);
        model.addAttribute("fastTotal", fast);
        model.addAttribute("dupTotal", dup);
        return "admin/activations";
    }

    /** 按当前筛选导出激活记录 */
    @GetMapping("/activations/export")
    public ResponseEntity<byte[]> exportActivations(@RequestParam(defaultValue = "all") String status,
                                                    @RequestParam(defaultValue = "") String code,
                                                    @RequestParam(defaultValue = "") String agent,
                                                    @RequestParam(name = "app", defaultValue = "") String appFilter,
                                                    @RequestParam(defaultValue = "") String q,
                                                    @RequestParam(defaultValue = "all") String range,
                                                    @RequestParam(defaultValue = "") String from,
                                                    @RequestParam(defaultValue = "") String to) {
        Long onlyApp = appFilter.matches("\\d{1,18}") ? Long.valueOf(appFilter) : null;
        List<ActivationRecord> rows = recordRepo.findAll(recordSpec(recordApps(), onlyApp, status, code.trim(), agent, q.trim(), window(range, from, to)),
                PageRequest.of(0, EXPORT_LIMIT, Sort.by(Sort.Direction.DESC, "activatedAt"))).getContent();
        Map<Long, String> codeAgent = codeAgents(rows);
        Map<Long, String> names = appNames();
        Csv csv = new Csv().row("提交时间", "App", "口令", "代理", "设备 ID", "IP", "状态", "确认时间");
        for (ActivationRecord r : rows) {
            csv.row(ui.full(r.getActivatedAt()), names.getOrDefault(r.getAppId(), ""), r.getCodeText(), codeAgent.getOrDefault(r.getCodeId(), ""),
                    r.getDeviceId(), r.getDeviceIp(), Boolean.TRUE.equals(r.getConfirmed()) ? "已确认" : r.getFastMs() != null ? "非正常" : r.getDupOf() != null ? "重复" : "待确认",
                    ui.full(r.getConfirmedAt()));
        }
        audit.log("RECORD_EXPORT", "导出 " + rows.size() + " 条激活记录");
        return csv.download("激活记录-" + fileTime() + ".csv");
    }

    /** 日志的时间范围：今天、近 7 / 30 / 90 天，0 表示全部 */
    static final List<Integer> LOG_DAYS = List.of(1, 7, 30, 90);

    /** 日志页和导出共用的筛选条件：类型、类别、App、关键字、时间；不是超级管理员只能看自己能看的 App */
    private Specification<OperationLog> logFilter(String type, String area, String query, Long onlyApp, int days) {
        AuditService.Area only = AuditService.area(area);
        CurrentAdmin me = access.current();
        List<Long> allowed = shell.visible().stream().map(OtaApp::getId).toList();
        java.time.LocalDateTime since = days <= 0 ? null : days == 1 ? ui.todayStart() : java.time.LocalDateTime.now().minusDays(days);
        return (root, cq, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (onlyApp != null) where.add(cb.equal(root.get("appId"), onlyApp));
            else if (!me.owner()) {
                Predicate mine = allowed.isEmpty() ? cb.disjunction() : root.get("appId").in(allowed);
                // 口令所有 App 通用，口令的日志不带 App：有口令权限的成员也能看
                if (me.canCodes()) mine = cb.or(mine, cb.and(cb.isNull(root.get("appId")),
                        cb.or(cb.like(root.get("action"), "CODE\\_%", '\\'), cb.equal(root.get("action"), "RECORD_EXPORT"))));
                where.add(mine);
            }
            if ("admin".equals(type)) where.add(cb.not(root.get("action").in(AuditService.DEVICE_EVENTS)));
            if ("device".equals(type)) where.add(root.get("action").in(AuditService.DEVICE_EVENTS));
            if (only != null) where.add(root.get("action").in(only.actions()));
            if (since != null) where.add(cb.greaterThanOrEqualTo(root.get("createdAt"), since));
            if (!query.isEmpty()) {
                String like = "%" + query.replace("%", "") + "%";
                List<String> byName = new ArrayList<>();
                for (com.ota.platform.admin.AdminUser u : adminUsers.all()) {
                    if (u.getName() != null && u.getName().contains(query)) byName.add(u.getUsername());
                }
                Predicate p = cb.or(cb.like(root.get("detail"), like), cb.like(root.get("operator"), like));
                where.add(byName.isEmpty() ? p : cb.or(p, root.get("operator").in(byName)));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    private Long logApp(String appFilter) {
        return shell.visible().stream().filter(a -> a.getSlug().equals(appFilter)).map(OtaApp::getId).findFirst().orElse(null);
    }

    @GetMapping("/logs")
    public String logs(@RequestParam(defaultValue = "all") String type,
                       @RequestParam(defaultValue = "") String area,
                       @RequestParam(defaultValue = "") String q,
                       @RequestParam(name = "app", defaultValue = "") String appFilter,
                       @RequestParam(defaultValue = "0") int days,
                       @RequestParam(defaultValue = "1") int page,
                       Model model, HttpSession session) {
        shell.populate(model, session);
        String query = q.trim();
        CurrentAdmin me = access.current();
        List<OtaApp> visible = shell.visible();
        Map<Long, String> appNames = new HashMap<>();
        for (OtaApp a : apps.all()) appNames.put(a.getId(), a.getName());
        Long onlyApp = logApp(appFilter);
        int range = LOG_DAYS.contains(days) ? days : 0;
        PageRequest pr = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        String areaKey = AuditService.area(area) == null ? "" : area;
        model.addAttribute("logs", logRepo.findAll(logFilter(type, areaKey, query, onlyApp, range), pr));
        model.addAttribute("type", type);
        model.addAttribute("area", areaKey);
        model.addAttribute("logAreas", AuditService.AREAS);
        model.addAttribute("q", query);
        model.addAttribute("days", range);
        model.addAttribute("appFilter", onlyApp == null ? "" : appFilter);
        model.addAttribute("appNames", appNames);
        model.addAttribute("logApps", visible);
        model.addAttribute("canClear", me.owner());
        Map<String, String> names = new HashMap<>();
        for (com.ota.platform.admin.AdminUser u : adminUsers.all()) names.put(u.getUsername(), u.getName());
        model.addAttribute("operatorNames", names);
        return "admin/logs";
    }

    // ==================== 运行模式 ====================

    @PostMapping("/set")
    public String setStyle(@RequestParam int style, HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        apps.setRedeemOpen(app, style == 2);
        audit.log("SWITCH", app.getSlug() + " 设为 " + (style == 2 ? "B面（上线）" : "A面（审核）"));
        events.modeSwitched(app, style == 2);
        ra.addFlashAttribute("msg", "已切换到 " + (style == 2 ? "B 面（上线模式）" : "A 面（审核模式）"));
        return "redirect:/admin";
    }

    /** 按当前筛选条件导出日志（最多 5 万条），和页面上看到的一致 */
    @GetMapping("/logs/export")
    public ResponseEntity<byte[]> exportLogs(@RequestParam(defaultValue = "all") String type, @RequestParam(defaultValue = "") String area,
                                             @RequestParam(defaultValue = "") String q,
                                             @RequestParam(name = "app", defaultValue = "") String appFilter,
                                             @RequestParam(defaultValue = "0") int days) {
        int range = LOG_DAYS.contains(days) ? days : 0;
        List<OperationLog> rows = logRepo.findAll(logFilter(type, area, q.trim(), logApp(appFilter), range),
                PageRequest.of(0, 50_000, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")))).getContent();
        Map<Long, String> appNames = new HashMap<>();
        for (OtaApp a : apps.all()) appNames.put(a.getId(), a.getName());
        Map<String, String> names = new HashMap<>();
        for (com.ota.platform.admin.AdminUser u : adminUsers.all()) names.put(u.getUsername(), u.getName());
        Csv csv = new Csv().row("时间", "类型", "App", "操作人", "操作", "详情");
        for (OperationLog l : rows) {
            String who = l.getOperator() == null ? "" : names.getOrDefault(l.getOperator(), l.getOperator());
            csv.row(ui.full(l.getCreatedAt()), AuditService.DEVICE_EVENTS.contains(l.getAction()) ? "设备事件" : "后台操作",
                    l.getAppId() == null ? "系统" : appNames.getOrDefault(l.getAppId(), ""), who, ui.action(l.getAction()), l.getDetail());
        }
        audit.log("LOG_EXPORT", "导出 " + rows.size() + " 条操作日志");
        return csv.download("操作日志-" + fileTime() + ".csv");
    }

    @PostMapping("/logs/clear")
    public String clearLogs(@RequestParam(defaultValue = "all") String type, RedirectAttributes ra) {
        long n;
        String what;
        switch (type) {
            case "admin" -> { n = logRepo.deleteByActionNotIn(AuditService.DEVICE_EVENTS); what = "后台操作日志"; }
            case "device" -> { n = logRepo.deleteByActionIn(AuditService.DEVICE_EVENTS); what = "设备事件日志"; }
            default -> { n = logRepo.count(); logRepo.deleteAllInBatch(); what = "全部日志"; }
        }
        audit.log("LOG_CLEAR", "清空" + what + "（" + n + " 条）");
        ra.addFlashAttribute("msg", "已清空" + what + "，共 " + n + " 条");
        return "redirect:/admin/logs" + ("all".equals(type) ? "" : "?type=" + type);
    }

    // ==================== 口令 ====================

    @PostMapping("/code/create")
    public String createCode(@RequestParam String code,
                             @RequestParam(defaultValue = "") String remark,
                             @RequestParam(defaultValue = "0") int maxUses,
                             @RequestParam(required = false) Long agentId,
                             @RequestParam(defaultValue = "") String channel,
                             @RequestParam(defaultValue = "") String expires,
                             HttpSession session, RedirectAttributes ra) {
        String back = "redirect:/admin/codes" + (agentId != null ? "?agent=" + agentId : "");
        try {
            ActivationCode c = codeAdmin.create(code, new CodeAdminService.Spec(remark, maxUses, agentId, channel, endOfDay(expires)));
            String owner = c.getAgentId() == null ? null : agentNames().get(c.getAgentId());
            ra.addFlashAttribute("msg", "已创建口令 " + c.getCode() + (owner != null ? "，归属 " + owner : ""));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return back;
    }

    /** 批量生成随机口令 */
    @PostMapping("/code/batch")
    public String batchCodes(@RequestParam(defaultValue = "10") int count,
                             @RequestParam(defaultValue = "") String prefix,
                             @RequestParam(defaultValue = "6") int length,
                             @RequestParam(defaultValue = "") String remark,
                             @RequestParam(defaultValue = "0") int maxUses,
                             @RequestParam(required = false) Long agentId,
                             @RequestParam(defaultValue = "") String channel,
                             @RequestParam(defaultValue = "") String expires,
                             HttpSession session, RedirectAttributes ra) {
        try {
            List<ActivationCode> made = codeAdmin.generate(count, prefix, length,
                    new CodeAdminService.Spec(remark, maxUses, agentId, channel, endOfDay(expires)));
            ra.addFlashAttribute("msg", "已生成 " + made.size() + " 个口令" + (agentId != null ? "，已分配给代理，TA 登录代理后台就能看到" : ""));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/codes" + (agentId != null ? "?agent=" + agentId : "");
    }

    /** 导入已有口令，每行一个 */
    @PostMapping("/code/import")
    public String importCodes(@RequestParam(defaultValue = "") String text,
                              @RequestParam(defaultValue = "") String remark,
                              @RequestParam(defaultValue = "0") int maxUses,
                              @RequestParam(required = false) Long agentId,
                              @RequestParam(defaultValue = "") String channel,
                              @RequestParam(defaultValue = "") String expires,
                              HttpSession session, RedirectAttributes ra) {
        try {
            CodeAdminService.Imported r = codeAdmin.importCodes(text,
                    new CodeAdminService.Spec(remark, maxUses, agentId, channel, endOfDay(expires)));
            ra.addFlashAttribute("msg", "已导入 " + r.created() + " 个口令"
                    + (r.existing() > 0 ? "，" + r.existing() + " 个原来就有、跳过了" : "")
                    + (r.invalid().isEmpty() ? "" : "，" + r.invalid().size() + " 个超过 64 位没导入"));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/codes" + (agentId != null ? "?agent=" + agentId : "");
    }

    @PostMapping("/code/update")
    public String updateCode(@RequestParam Long id,
                             @RequestParam(defaultValue = "") String remark,
                             @RequestParam(defaultValue = "0") int maxUses,
                             @RequestParam(required = false) Long agentId,
                             @RequestParam(defaultValue = "") String channel,
                             @RequestParam(required = false) String expires,
                             @RequestParam(defaultValue = "") String back,
                             HttpSession session, RedirectAttributes ra) {
        try {
            ActivationCode c = codeAdmin.update(id, remark, maxUses, agentId, true, channel, true, null,
                    endOfDay(expires), expires != null);
            ra.addFlashAttribute("msg", "已保存口令 " + c.getCode());
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/codes" + safeQuery(back);
    }

    @PostMapping("/code/toggle")
    public String toggleCode(@RequestParam Long id, @RequestParam(defaultValue = "") String back,
                             HttpSession session, RedirectAttributes ra) {
        try {
            ActivationCode c = codeAdmin.toggle(id);
            ra.addFlashAttribute("msg", "口令 " + c.getCode() + " 已" + (c.getEnabled() ? "启用" : "停用"));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/codes" + safeQuery(back);
    }

    @PostMapping("/code/delete")
    public String deleteCode(@RequestParam Long id, @RequestParam(defaultValue = "") String back,
                             HttpSession session, RedirectAttributes ra) {
        try {
            ActivationCode c = codeAdmin.delete(id);
            ra.addFlashAttribute("msg", "已删除口令 " + c.getCode());
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/codes" + safeQuery(back);
    }

    /** 批量启用、停用、分配代理、删除勾选的口令 */
    @PostMapping("/code/bulk")
    public String bulkCodes(@RequestParam(name = "ids", required = false) List<Long> ids,
                            @RequestParam(defaultValue = "") String action,
                            @RequestParam(required = false) Long agentId,
                            @RequestParam(defaultValue = "") String expiresAt,
                            @RequestParam(required = false) Integer maxUses,
                            @RequestParam(defaultValue = "") String remark,
                            @RequestParam(defaultValue = "") String back,
                            HttpSession session, RedirectAttributes ra) {
        try {
            int n = switch (action) {
                case "expire" -> codeAdmin.bulkExpiry(ids, endOfDay(expiresAt));
                case "uses" -> codeAdmin.bulkMaxUses(ids, maxUses);
                case "remark" -> codeAdmin.bulkRemark(ids, remark);
                default -> codeAdmin.bulk(ids, action, agentId);
            };
            String done = switch (action) {
                case "enable" -> "已启用";
                case "disable" -> "已停用";
                case "assign" -> agentId == null || agentId <= 0 ? "已取消分配" : "已分配给 " + agentNames().getOrDefault(agentId, "代理");
                default -> "已删除";
            };
            String msg = switch (action) {
                case "expire" -> "已把 " + n + " 个口令" + (expiresAt.isBlank() ? "改成一直有效" : "的有效期改到 " + expiresAt.trim());
                case "uses" -> "已把 " + n + " 个口令改成每个可激活 " + (maxUses == null || maxUses == 0 ? "不限次数" : maxUses + " 次");
                case "remark" -> "已把 " + n + " 个口令的备注" + (remark.isBlank() ? "清空" : "改成「" + remark.trim() + "」");
                default -> done + " " + n + " 个口令";
            };
            ra.addFlashAttribute("msg", msg);
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/codes" + safeQuery(back);
    }

    // ==================== 内部 ====================

    /**
     * 口令筛选。agent：空=全部，none=未分配，数字=某个代理；status：all / usable / disabled / expired / exhausted；
     * q：口令或备注里包含的文字。状态的判断顺序和 AdminUi.codeStatus 一致
     */
    static Specification<ActivationCode> codeSpec(String agent, String status, String q) {
        return (root, cq, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if ("none".equals(agent)) where.add(cb.isNull(root.get("agentId")));
            else if (agent != null && agent.matches("\\d{1,18}")) where.add(cb.equal(root.get("agentId"), Long.valueOf(agent)));
            if (q != null && !q.isBlank()) {
                String like = "%" + q.trim().replace("%", "") + "%";
                where.add(cb.or(cb.like(root.get("code"), like), cb.like(root.get("remark"), like)));
            }
            Expression<Integer> max = cb.coalesce(root.<Integer>get("maxUses"), 0);
            // 次数按代理看到的算（见 ActivationCode.seenCount），没有就按实际的
            Expression<Integer> used = cb.coalesce(root.<Integer>get("seenCount"), cb.coalesce(root.<Integer>get("usedCount"), 0));
            Predicate enabled = cb.isTrue(root.get("enabled"));
            Predicate notExpired = cb.or(cb.isNull(root.get("expiresAt")), cb.greaterThanOrEqualTo(root.get("expiresAt"), LocalDateTime.now()));
            Predicate exhausted = cb.and(cb.greaterThan(max, 0), cb.greaterThanOrEqualTo(used, max));
            switch (status == null ? "all" : status) {
                case "usable" -> where.add(cb.and(enabled, notExpired, cb.not(exhausted)));
                case "disabled" -> where.add(cb.or(cb.isNull(root.get("enabled")), cb.isFalse(root.get("enabled"))));
                case "expired" -> where.add(cb.and(enabled, cb.isNotNull(root.get("expiresAt")), cb.lessThan(root.get("expiresAt"), LocalDateTime.now())));
                case "exhausted" -> where.add(cb.and(enabled, notExpired, exhausted));
                default -> { }
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    /** 成员只能看自己能看的 App 的激活记录；超级管理员是 null，不限 */
    private Collection<Long> recordApps() {
        CurrentAdmin me = access.current();
        return me != null && me.owner() ? null : shell.visible().stream().map(OtaApp::getId).toList();
    }

    /** 激活记录筛选，q 匹配设备 ID、IP 或口令；allowed 是能看的 App（null 不限），onlyApp 为空是其中所有 App */
    private static Specification<ActivationRecord> recordSpec(Collection<Long> allowed, Long onlyApp, String status, String codeFilter,
                                                              String agent, String query, Window w) {
        return (root, cq, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (allowed != null) where.add(allowed.isEmpty() ? cb.disjunction() : root.get("appId").in(allowed));
            if (onlyApp != null) where.add(cb.equal(root.get("appId"), onlyApp));
            if ("confirmed".equals(status)) {
                where.add(cb.isTrue(root.get("confirmed")));
            } else if ("pending".equals(status)) {
                where.add(cb.or(cb.isNull(root.get("confirmed")), cb.isFalse(root.get("confirmed"))));
                where.add(cb.isNull(root.get("fastMs")));
                where.add(cb.isNull(root.get("dupOf")));
            } else if ("fast".equals(status)) {
                where.add(cb.isNotNull(root.get("fastMs")));
            } else if ("dup".equals(status)) {
                where.add(cb.isNotNull(root.get("dupOf")));
            }
            if (!agent.isEmpty()) {
                var sub = cq.subquery(Long.class);
                var c = sub.from(ActivationCode.class);
                sub.select(c.get("id")).where("none".equals(agent) ? cb.isNull(c.get("agentId"))
                        : cb.equal(c.get("agentId"), agent.matches("\\d{1,18}") ? Long.valueOf(agent) : -1L));
                where.add(root.get("codeId").in(sub));
            }
            if (!codeFilter.isEmpty()) where.add(cb.equal(root.get("codeText"), codeFilter));
            if (!query.isEmpty()) {
                String like = "%" + query.replace("%", "") + "%";
                where.add(cb.or(cb.like(root.get("deviceId"), like), cb.like(root.get("deviceIp"), like), cb.like(root.get("codeText"), like)));
            }
            if (w.from() != null) where.add(cb.greaterThanOrEqualTo(root.get("activatedAt"), w.from()));
            if (w.to() != null) where.add(cb.lessThan(root.get("activatedAt"), w.to()));
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    private Map<Long, String> agentNames() {
        Map<Long, String> names = new HashMap<>();
        for (Agent a : agentRepo.findAll()) names.put(a.getId(), a.getName());
        return names;
    }

    /** 这些记录的口令各归哪个代理：口令 ID → 代理名 */
    private Map<Long, String> codeAgents(List<ActivationRecord> records) {
        Map<Long, String> names = agentNames();
        Map<Long, String> out = new HashMap<>();
        for (ActivationCode c : codeRepo.findAllById(records.stream().map(ActivationRecord::getCodeId).distinct().toList())) {
            if (c.getAgentId() != null && names.containsKey(c.getAgentId())) out.put(c.getId(), names.get(c.getAgentId()));
        }
        return out;
    }

    private Map<Long, String> appNames() {
        Map<Long, String> names = new HashMap<>();
        for (OtaApp a : apps.all()) names.put(a.getId(), a.getName());
        return names;
    }

    /** 有效期：展示时区那天的 23:59:59，换成服务器时区；空表示不限 */
    private LocalDateTime endOfDay(String date) {
        if (date == null || date.isBlank()) return null;
        LocalDate d = parseDate(date);
        if (d == null) throw ApiException.badRequest("BAD_DATE", "有效期的日期格式不对");
        return ui.startOf(d.plusDays(1)).minusSeconds(1);
    }

    private String fileTime() {
        return FILE_TIME.format(LocalDateTime.now(ZoneId.of(ui.zoneId())));
    }

    /** 回跳时只保留口令页的筛选参数 */
    private static String safeQuery(String back) {
        return back != null && back.matches("[A-Za-z0-9=&%._+\\-]{1,300}") ? "?" + back : "";
    }
}
