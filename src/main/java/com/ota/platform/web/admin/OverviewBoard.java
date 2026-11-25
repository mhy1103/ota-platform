package com.ota.platform.web.admin;

import com.ota.platform.admin.AdminUser;
import com.ota.platform.admin.AdminUserRepo;
import com.ota.platform.admin.AppPerm;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.agent.AgentService;
import com.ota.platform.agent.CodeRequest;
import com.ota.platform.app.OtaApp;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.codes.ActivationRecordRepo;
import com.ota.platform.install.InstallService;
import com.ota.platform.installer.InstallerService;
import com.ota.platform.notice.NoticeService;
import com.ota.platform.ota.LaunchErrorService;
import com.ota.platform.ota.ReleaseStat;
import com.ota.platform.ota.ReleaseStatsService;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.schedule.ScheduleService;
import com.ota.platform.schedule.ScheduledTask;
import com.ota.platform.security.LoginRecord;
import com.ota.platform.security.LoginRecordRepo;
import com.ota.platform.stats.DeviceSeenRepository;
import com.ota.platform.stats.StatsService;
import com.ota.platform.storage.BlobStore;
import com.ota.platform.web.AdminUi;
import com.ota.platform.web.Prefs;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 概览看板：有哪些卡片，每个人显示哪些、什么顺序、多宽，和卡片显示时才查的数据 */
@Component
public class OverviewBoard {

    /** 宽度按一行 12 列算：3 是四分之一行，4 三分之一，6 一半，8 三分之二，12 整行。size 是默认宽度，sizes 是能调成的 */
    public record Def(String id, String name, String desc, String group, int size, List<Integer> sizes, String icon, boolean standard) {}

    /** 「添加卡片」里的一张 */
    public record Card(String id, String name, String desc, String icon, String sizes, boolean on) {}

    public record Group(String name, List<Card> cards) {}

    public record ReqRow(Long id, String agent, int count, boolean custom, String note, LocalDateTime at) {}

    public record CodeRow(String code, String agent, LocalDateTime expiresAt, long daysLeft) {}

    public record TopCode(String code, String agent, long count, int pct, int usedCount, int maxUses) {}

    public record WeekDay(String label, long cur, long prev, int px, int prevPx, boolean today, boolean later) {}

    public record HeatCell(int hour, long n, int lv) {}

    public record HeatRow(String label, List<HeatCell> cells) {}

    public record Roll(String channel, String runtime, String releaseId, String message, int rollout, long installed, long offered,
                       String previousId) {}

    public record Plan(Long id, String kind, String title, String sub, LocalDateTime runAt, String face, Integer percent,
                       String status, String result, LocalDateTime doneAt, boolean canCancel) {}

    public record Sys(String version, String uptime, int memPct, String mem, int diskPct, String disk, boolean diskLow,
                      String storage, String database) {}

    public record Login(String name, String device, boolean mobile, boolean success, String reason, Instant at) {}

    private static List<Integer> w(Integer... sizes) {
        return List.of(sizes);
    }

    public static final List<Def> ALL = List.of(
            new Def("todo", "待处理", "代理申请、启动失败、签名和备份提醒", "常用", 6, w(4, 6, 8, 12), "i-bell", true),
            new Def("mode", "运行模式", "A/B 面切换和定时切换", "常用", 6, w(4, 6, 8, 12), "i-zap", true),
            new Def("steps", "开始使用", "接入、发布、新建口令、下载备份，做完自动收起", "常用", 12, w(8, 12), "i-compass", true),
            new Def("quick", "快捷操作", "新建口令、上传更新包、新建代理这些常用入口", "常用", 4, w(3, 4, 6, 8, 12), "i-command", false),
            new Def("note", "便签", "记点自己要办的事，只有你看得到", "常用", 4, w(3, 4, 6), "i-note", false),
            new Def("goal", "今日目标", "给今天的有效激活定个数，看完成了多少", "常用", 3, w(3, 4, 6), "i-target", false),
            new Def("today", "今日有效激活", "今天的激活、每小时分布和高峰", "激活", 12, w(8, 12), "i-activity", true),
            new Def("total", "累计有效激活", "累计激活和近 7 天的变化", "激活", 4, w(3, 4, 6), "i-trend-up", true),
            new Def("trend", "有效激活趋势", "近 7 / 14 / 30 天每天的有效激活", "激活", 8, w(6, 8, 12), "i-chart", true),
            new Def("week", "本周对比", "这周每天的有效激活，和上周同一天比", "激活", 4, w(3, 4, 6), "i-calendar", false),
            new Def("funnel", "激活转化", "提交了多少、确认了多少，今天和近 7 天", "激活", 3, w(3, 4, 6), "i-funnel", false),
            new Def("heat", "激活时段", "近 4 周每天每个钟点的有效激活，看高峰在什么时候", "激活", 8, w(6, 8, 12), "i-grid", false),
            new Def("records", "最近激活", "最新的 8 条激活记录", "激活", 6, w(4, 6, 8, 12), "i-inbox", true),
            new Def("codes", "口令", "可用、已用完、已过期、停用各多少", "口令与代理", 4, w(3, 4, 6), "i-key", true),
            new Def("topcodes", "热门口令", "近 7 天在这个 App 里激活最多的口令", "口令与代理", 4, w(4, 6, 8), "i-flame", false),
            new Def("expiring", "快到期的口令", "7 天内到期的口令", "口令与代理", 4, w(3, 4, 6), "i-clock", false),
            new Def("agents", "代理", "启用中、今天有激活、待审核", "口令与代理", 4, w(3, 4, 6), "i-users", true),
            new Def("ranking", "代理排行", "按今天的有效激活排名", "口令与代理", 6, w(4, 6, 8, 12), "i-trend-up", true),
            new Def("requests", "口令申请", "等审核的代理口令申请", "口令与代理", 4, w(3, 4, 6), "i-inbox", false),
            new Def("notices", "代理公告", "最近发的公告，多少代理看过", "口令与代理", 4, w(4, 6, 8), "i-megaphone", false),
            new Def("hot", "热更新", "最新发布、版本覆盖、启动崩溃、增量更新省的流量", "发布", 12, w(6, 12), "i-layers", true),
            new Def("live", "线上版本", "每个渠道、Runtime 现在用的版本", "发布", 4, w(4, 6, 8, 12), "i-package", true),
            new Def("rollout", "灰度进度", "正在灰度的版本推给了多少设备、装上了多少", "发布", 6, w(4, 6, 8, 12), "i-sliders", false),
            new Def("coverage", "版本覆盖", "每个版本有多少台设备在用", "发布", 6, w(4, 6, 8, 12), "i-phone", true),
            new Def("plans", "计划任务", "等着执行的定时切换、定时调灰度，和最近执行过的", "发布", 6, w(4, 6, 8, 12), "i-calendar", false),
            new Def("deploys", "发布记录", "最近的发布、调灰度、回滚", "发布", 6, w(4, 6, 8, 12), "i-history", false),
            new Def("errors", "最近启动错误", "App 启动崩溃后带上来的错误", "发布", 6, w(6, 8, 12), "i-alert", false),
            new Def("installers", "安装包", "最近上传的 iOS、Android 安装包", "发布", 4, w(4, 6, 8), "i-download", false),
            new Def("devices", "今日活跃设备", "今天的活跃和新设备、近 14 天每天的活跃", "设备与系统", 12, w(8, 12), "i-phone", true),
            new Def("mix", "设备构成", "平台、渠道、Runtime 各占多少", "设备与系统", 6, w(4, 6, 8, 12), "i-pie", false),
            new Def("ops", "最近操作", "这个 App 最近的操作日志", "设备与系统", 6, w(4, 6, 8, 12), "i-file", true),
            new Def("system", "运行状态", "版本、已运行多久、内存和磁盘", "设备与系统", 4, w(3, 4, 6), "i-server", false),
            new Def("logins", "登录动态", "管理员最近的登录，没登上的标红", "设备与系统", 4, w(4, 6, 8), "i-shield", false));

    private static final List<String> GROUPS = List.of("常用", "激活", "口令与代理", "发布", "设备与系统");

    /** 没调整过看板时显示的卡片和顺序 */
    private static final List<String> DEFAULT = List.of("todo", "mode", "steps", "today", "devices", "codes", "agents", "total", "hot",
            "trend", "live", "coverage", "ranking", "records", "ops");

    private static final Map<String, Def> BY_ID = ALL.stream().collect(Collectors.toUnmodifiableMap(Def::id, d -> d));

    private final Prefs prefs;
    private final AdminUserRepo admins;
    private final AgentService agentService;
    private final AgentRepo agentRepo;
    private final ActivationCodeRepo codeRepo;
    private final ActivationRecordRepo recordRepo;
    private final LaunchErrorService launchErrors;
    private final ScheduleService schedules;
    private final ReleaseService releases;
    private final ReleaseStatsService releaseStats;
    private final DeviceSeenRepository devices;
    private final StatsService stats;
    private final InstallerService installers;
    private final NoticeService notices;
    private final LoginRecordRepo logins;
    private final InstallService install;
    private final BlobStore blobs;
    private final ObjectProvider<BuildProperties> build;
    private final AdminUi ui;

    public OverviewBoard(Prefs prefs, AdminUserRepo admins, AgentService agentService, AgentRepo agentRepo, ActivationCodeRepo codeRepo,
                         ActivationRecordRepo recordRepo, LaunchErrorService launchErrors, ScheduleService schedules, ReleaseService releases,
                         ReleaseStatsService releaseStats, DeviceSeenRepository devices, StatsService stats, InstallerService installers,
                         NoticeService notices, LoginRecordRepo logins, InstallService install, BlobStore blobs,
                         ObjectProvider<BuildProperties> build, AdminUi ui) {
        this.prefs = prefs;
        this.admins = admins;
        this.agentService = agentService;
        this.agentRepo = agentRepo;
        this.codeRepo = codeRepo;
        this.recordRepo = recordRepo;
        this.launchErrors = launchErrors;
        this.schedules = schedules;
        this.releases = releases;
        this.releaseStats = releaseStats;
        this.devices = devices;
        this.stats = stats;
        this.installers = installers;
        this.notices = notices;
        this.logins = logins;
        this.install = install;
        this.blobs = blobs;
        this.build = build;
        this.ui = ui;
    }

    /** 一项布局：排法（/ 新的一行，+ 叠在前一张下面，~ 同一行另起一列，没有就是还没排过）、卡片 ID、宽度（没写是 0）、
     *  拉过的高度（像素，没拉过是 0）。ID 里也有 r、x（records、mix），所以 ID 不贪心匹配；中间有一版存的是占几行（r2），读到就丢掉 */
    record Entry(String hint, String id, int size, int height) {}

    private static final Pattern ENTRY = Pattern.compile("([/+~])?([a-z]{2,16}?)(?::(\\d{1,2}))?(?:r[1-3])?(?:x(\\d{2,4}))?");
    static final int MIN_H = 80, MAX_H = 2000;

    static Entry parse(String entry) {
        Matcher m = ENTRY.matcher(entry == null ? "" : entry.trim());
        if (!m.matches()) return null;
        int h = m.group(4) == null ? 0 : Integer.parseInt(m.group(4));
        return new Entry(m.group(1) == null ? "" : m.group(1), m.group(2), m.group(3) == null ? 0 : Integer.parseInt(m.group(3)),
                h >= MIN_H && h <= MAX_H ? h : 0);
    }

    /** 存的格式：排法、卡片 ID，宽度不是默认的才带上，拉过高度才带 x，比如 /trend:6、~ops:6x720；不认识的卡片返回 null */
    public static String normalize(String entry) {
        Entry e = parse(entry);
        Def d = e == null ? null : BY_ID.get(e.id());
        if (d == null) return null;
        String out = e.hint() + (d.sizes().contains(e.size()) && e.size() != d.size() ? d.id() + ":" + e.size() : d.id());
        return e.height() > 0 ? out + "x" + e.height() : out;
    }

    private static boolean allowed(Def d, OtaApp app, CurrentAdmin me) {
        return switch (d.id()) {
            case "agents", "ranking", "requests", "notices" -> me.canManageAgents();
            case "steps" -> me.can(app.getId(), AppPerm.SETTINGS);
            case "system", "logins" -> me.owner();
            default -> true;
        };
    }

    public void fill(Model model, OtaApp app, CurrentAdmin me) {
        Map<String, Def> usable = new LinkedHashMap<>();
        for (Def d : ALL) if (allowed(d, app, me)) usable.put(d.id(), d);
        String stored = admins.findById(me.id()).map(AdminUser::getPrefs).orElse(null);
        List<String> saved = prefs.board(stored);
        List<String> shown = new ArrayList<>();
        Map<String, Integer> size = new LinkedHashMap<>();
        Map<String, Integer> heights = new LinkedHashMap<>();
        Map<String, String> hints = new HashMap<>();
        if (saved == null) {
            for (String id : DEFAULT) {
                if (!usable.containsKey(id)) continue;
                shown.add(id);
                size.put(id, usable.get(id).size());
            }
            Set<String> hidden = hidden(model);
            pack(shown, size, hidden, usable);
            // 默认布局的行是排好的：带上「新的一行 / 同一行」，页面上按这个原样拼，内容少的卡片不会被挪到别的卡片下面
            int used = 0;
            for (String id : shown) {
                if (hidden.contains(id)) continue;
                int s = size.get(id);
                if (used == 0 || used + s > 12) {
                    hints.put(id, "/");
                    used = s;
                } else {
                    hints.put(id, "~");
                    used += s;
                }
                if (used >= 12) used = 0;
            }
        } else {
            for (String entry : saved) {
                Entry e = parse(normalize(entry));
                Def d = e == null ? null : usable.get(e.id());
                if (d == null || size.containsKey(d.id())) continue;
                shown.add(d.id());
                size.put(d.id(), e.size() > 0 ? e.size() : d.size());
                if (e.height() > 0) heights.put(d.id(), e.height());
                if (!e.hint().isEmpty()) hints.put(d.id(), e.hint());
            }
        }

        Map<String, Integer> order = new LinkedHashMap<>();
        for (String id : shown) order.put(id, order.size());
        Map<String, String> sizes = new HashMap<>();
        for (Def d : usable.values()) sizes.put(d.id(), d.sizes().stream().map(String::valueOf).collect(Collectors.joining(",")));
        List<Group> groups = new ArrayList<>();
        for (String g : GROUPS) {
            List<Card> cards = usable.values().stream().filter(d -> d.group().equals(g))
                    .map(d -> new Card(d.id(), d.name(), d.desc(), d.icon(), sizes.get(d.id()), order.containsKey(d.id()))).toList();
            if (!cards.isEmpty()) groups.add(new Group(g, cards));
        }
        model.addAttribute("boardOrder", order);
        model.addAttribute("boardSize", size);
        model.addAttribute("boardSizes", sizes);
        model.addAttribute("boardLayout", shown.stream().map(id -> {
            String e = hints.getOrDefault(id, "") + id + ":" + size.get(id);
            return heights.containsKey(id) ? e + "x" + heights.get(id) : e;
        }).collect(Collectors.joining(",")));
        model.addAttribute("boardGroups", groups);
        model.addAttribute("boardCustom", saved != null);
        model.addAttribute("boardSig", Prefs.boardSig(saved));
        model.addAttribute("boardCount", usable.size());

        data(model, app, me, order.keySet(), stored);
    }

    /** 模板里按条件不画的卡片：开始使用做完了，还没有代理 */
    private static Set<String> hidden(Model model) {
        Set<String> out = new HashSet<>();
        if (model.getAttribute("stepsDone") instanceof Long done && model.getAttribute("steps") instanceof List<?> steps && done >= steps.size()) {
            out.add("steps");
        }
        if (model.getAttribute("agentCount") instanceof Integer n && n == 0) out.add("ranking");
        return out;
    }

    /** 默认布局里有卡片不显示时，同一行剩下的卡片匀开，把空位占满 */
    static void pack(List<String> ids, Map<String, Integer> size, Set<String> hidden, Map<String, Def> defs) {
        List<String> row = new ArrayList<>();
        int used = 0;
        for (String id : ids) {
            if (hidden.contains(id)) continue;
            int s = size.get(id);
            if (used + s > 12) {
                widen(row, size, defs);
                row = new ArrayList<>();
                used = 0;
            }
            row.add(id);
            used += s;
        }
        widen(row, size, defs);
    }

    private static void widen(List<String> row, Map<String, Integer> size, Map<String, Def> defs) {
        if (row.isEmpty() || row.stream().mapToInt(size::get).sum() >= 12) return;
        int[] best = search(row, 0, new int[row.size()], 0, size, defs, null);
        if (best != null) for (int i = 0; i < row.size(); i++) size.put(row.get(i), best[i]);
    }

    /** 每张卡片挑一个不比现在窄的宽度，加起来正好 12；有几种就挑宽窄最接近的 */
    private static int[] search(List<String> row, int i, int[] pick, int sum, Map<String, Integer> size, Map<String, Def> defs, int[] best) {
        if (i == row.size()) {
            if (sum != 12) return best;
            if (best == null || spread(pick) < spread(best)) return pick.clone();
            return best;
        }
        for (int s : defs.get(row.get(i)).sizes()) {
            if (s < size.get(row.get(i)) || sum + s > 12) continue;
            pick[i] = s;
            best = search(row, i + 1, pick, sum + s, size, defs, best);
        }
        return best;
    }

    private static int spread(int[] v) {
        int min = Integer.MAX_VALUE, max = 0;
        for (int x : v) {
            min = Math.min(min, x);
            max = Math.max(max, x);
        }
        return max - min;
    }

    // ==================== 卡片的数据，显示了才查 ====================

    private void data(Model model, OtaApp app, CurrentAdmin me, Set<String> on, String stored) {
        LocalDate today = ui.today();
        LocalDateTime now = LocalDateTime.now();
        if (on.contains("expiring")) {
            List<ActivationCode> list = codeRepo.findAll((root, cq, cb) -> cb.and(cb.isTrue(root.get("enabled")),
                    cb.between(root.get("expiresAt"), now, now.plusDays(7))), PageRequest.of(0, 6, Sort.by("expiresAt"))).getContent();
            Map<Long, String> names = agentNames();
            model.addAttribute("boardExpiring", list.stream().map(c -> new CodeRow(c.getCode(), c.getAgentId() == null ? null : names.get(c.getAgentId()),
                    c.getExpiresAt(), ChronoUnit.DAYS.between(today, ui.localDate(c.getExpiresAt())))).toList());
        }
        if (on.contains("requests")) {
            Map<Long, String> names = agentNames();
            model.addAttribute("boardRequests", agentService.list(CodeRequest.PENDING, 5).stream()
                    .map(r -> new ReqRow(r.getId(), names.getOrDefault(r.getAgentId(), "（已删除）"), r.getCount(), r.isCustom(), r.getNote(),
                            r.getCreatedAt())).toList());
        }
        if (on.contains("mix")) {
            Instant since = Instant.now().minus(Duration.ofDays(30));
            Map<String, Long> byPlatform = stats.devicesByPlatform(app.getId());
            model.addAttribute("mixIos", byPlatform.getOrDefault("ios", 0L));
            model.addAttribute("mixAndroid", byPlatform.getOrDefault("android", 0L));
            model.addAttribute("mixChannels", AdminDevicesController.shares(devices.countByChannel(app.getId(), since), "production"));
            model.addAttribute("mixRuntimes", AdminDevicesController.shares(devices.countByRuntime(app.getId(), since), "未上报"));
        }
        if (on.contains("errors")) model.addAttribute("boardErrors", launchErrors.recent(app.getId()).stream().limit(5).toList());
        if (on.contains("plans")) plans(model, app, me);
        if (on.contains("deploys")) model.addAttribute("boardDeploys", releases.history(app, 6));
        if (on.contains("topcodes")) topCodes(model, app, now);
        if (on.contains("week")) week(model, app, today);
        if (on.contains("heat")) heat(model, app, today);
        if (on.contains("funnel")) {
            LocalDateTime since = ui.startOf(today.minusDays(6));
            model.addAttribute("funnelSubmitted7", recordRepo.countByAppIdAndActivatedAtGreaterThanEqual(app.getId(), since));
            model.addAttribute("funnelConfirmed7", recordRepo.countByAppIdAndConfirmedTrueAndConfirmedAtGreaterThanEqual(app.getId(), since));
        }
        if (on.contains("rollout")) rollout(model);
        if (on.contains("installers")) model.addAttribute("boardInstallers", installers.list(app).stream().limit(4).toList());
        if (on.contains("notices")) model.addAttribute("boardNotices", notices.adminList().stream().limit(3).toList());
        if (on.contains("system")) model.addAttribute("boardSys", system());
        if (on.contains("logins")) loginFeed(model);
        if (on.contains("note")) model.addAttribute("boardNote", prefs.cardData(stored, "note"));
        if (on.contains("goal")) {
            String g = prefs.cardData(stored, "goal");
            model.addAttribute("boardGoal", g == null ? null : Long.valueOf(g));
        }
    }

    private void plans(Model model, OtaApp app, CurrentAdmin me) {
        Map<String, String> names = adminNames();
        model.addAttribute("boardPlans", schedules.pending(app.getId()).stream().limit(5).map(t -> plan(t, app, me, names)).toList());
        model.addAttribute("boardPlansDone", schedules.recent(app.getId()).stream().limit(3).map(t -> plan(t, app, me, names)).toList());
    }

    private static Plan plan(ScheduledTask t, OtaApp app, CurrentAdmin me, Map<String, String> names) {
        boolean mode = ScheduledTask.MODE.equals(t.getKind());
        String who = t.getCreatedBy() == null ? "" : names.getOrDefault(t.getCreatedBy(), t.getCreatedBy());
        String title = mode ? ("B".equals(t.getFace()) ? "切换到 B 面" : "切换到 A 面") : t.getChannel() + " 灰度调到 " + t.getPercent() + "%";
        String sub = (mode ? ("B".equals(t.getFace()) ? "上线模式" : "审核模式") : "Runtime " + t.getRuntime())
                + (who.isEmpty() ? "" : " · " + who + " 设的");
        boolean cancel = t.isPending() && me.can(app.getId(), mode ? AppPerm.MODE : AppPerm.RELEASE);
        return new Plan(t.getId(), t.getKind(), title, sub, t.getRunAt(), t.getFace(), t.getPercent(), t.getStatus(), t.getResult(),
                t.getDoneAt(), cancel);
    }

    private void topCodes(Model model, OtaApp app, LocalDateTime now) {
        List<Object[]> rows = recordRepo.topCodes(app.getId(), now.minusDays(7), PageRequest.of(0, 5));
        Map<Long, ActivationCode> codes = new HashMap<>();
        codeRepo.findAllById(rows.stream().map(r -> (Long) r[0]).toList()).forEach(c -> codes.put(c.getId(), c));
        Map<Long, String> names = agentNames();
        long top = rows.isEmpty() ? 0 : (Long) rows.get(0)[2];
        List<TopCode> out = new ArrayList<>();
        for (Object[] r : rows) {
            ActivationCode c = codes.get((Long) r[0]);
            long n = (Long) r[2];
            out.add(new TopCode((String) r[1], c == null || c.getAgentId() == null ? null : names.get(c.getAgentId()), n,
                    top == 0 ? 0 : (int) Math.max(4, Math.round(n * 100.0 / top)),
                    c == null ? 0 : c.limitUsed(), c == null || c.getMaxUses() == null ? 0 : c.getMaxUses()));
        }
        model.addAttribute("boardTopCodes", out);
    }

    /** 这周一到今天每天的有效激活，和上周同一天比；柱子最高 72px */
    private void week(Model model, OtaApp app, LocalDate today) {
        LocalDate monday = today.with(DayOfWeek.MONDAY), last = monday.minusWeeks(1);
        long[] cur = new long[7], prev = new long[7];
        for (LocalDateTime t : recordRepo.confirmedTimesSince(app.getId(), ui.startOf(last))) {
            if (t == null) continue;
            LocalDate d = ui.localDate(t);
            int i = d.getDayOfWeek().getValue() - 1;
            if (!d.isBefore(monday)) cur[i]++;
            else prev[i]++;
        }
        int at = today.getDayOfWeek().getValue() - 1;
        long max = 0, sumCur = 0, sumPrev = 0;
        for (int i = 0; i < 7; i++) {
            max = Math.max(max, Math.max(i <= at ? cur[i] : 0, prev[i]));
            if (i <= at) {
                sumCur += cur[i];
                sumPrev += prev[i];
            }
        }
        String labels = "一二三四五六日";
        List<WeekDay> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            boolean later = i > at;
            days.add(new WeekDay(labels.substring(i, i + 1), later ? 0 : cur[i], prev[i], later ? 0 : px(cur[i], max, 72), px(prev[i], max, 72),
                    i == at, later));
        }
        model.addAttribute("weekDays", days);
        model.addAttribute("weekCur", sumCur);
        model.addAttribute("weekPrev", sumPrev);
        model.addAttribute("weekPrevAll", java.util.Arrays.stream(prev).sum());
        model.addAttribute("weekVs", AdminController.delta(sumCur, sumPrev));
    }

    /** 近 28 天按星期几、钟点统计有效激活；颜色分 4 档 */
    private void heat(Model model, OtaApp app, LocalDate today) {
        long[][] grid = new long[7][24];
        ZoneId zone = ZoneId.of(ui.zoneId());
        long total = 0;
        for (LocalDateTime t : recordRepo.confirmedTimesSince(app.getId(), ui.startOf(today.minusDays(27)))) {
            if (t == null) continue;
            ZonedDateTime z = t.atZone(ZoneId.systemDefault()).withZoneSameInstant(zone);
            grid[z.getDayOfWeek().getValue() - 1][z.getHour()]++;
            total++;
        }
        long max = 0;
        int peakDay = 0, peakHour = 0;
        for (int d = 0; d < 7; d++) for (int h = 0; h < 24; h++) {
            if (grid[d][h] > max) {
                max = grid[d][h];
                peakDay = d;
                peakHour = h;
            }
        }
        String labels = "一二三四五六日";
        List<HeatRow> rows = new ArrayList<>();
        for (int d = 0; d < 7; d++) {
            List<HeatCell> cells = new ArrayList<>();
            for (int h = 0; h < 24; h++) {
                long n = grid[d][h];
                cells.add(new HeatCell(h, n, n == 0 ? 0 : (int) Math.min(4, Math.ceil(n * 4.0 / max))));
            }
            rows.add(new HeatRow("周" + labels.charAt(d), cells));
        }
        model.addAttribute("heatRows", rows);
        model.addAttribute("heatTotal", total);
        model.addAttribute("heatPeak", max == 0 ? null : "周" + labels.charAt(peakDay) + " " + peakHour + " 点最多，4 周共 " + max + " 台");
    }

    @SuppressWarnings("unchecked")
    private void rollout(Model model) {
        List<ReleaseViews.Live> live = model.getAttribute("live") instanceof List<?> l ? (List<ReleaseViews.Live>) l : List.of();
        List<ReleaseViews.Live> rolling = live.stream()
                .filter(x -> x.getRelease() != null && !x.isRollbackToEmbedded() && x.getRollout() < 100).toList();
        Map<String, ReleaseStat> st = releaseStats.get(rolling.stream().map(x -> x.getRelease().getId()).toList());
        model.addAttribute("boardRollouts", rolling.stream().map(x -> {
            ReleaseStat s = st.get(x.getRelease().getId());
            return new Roll(x.getChannel(), x.getRuntime(), x.getRelease().getId(), x.getRelease().getMessage(), x.getRollout(),
                    s == null ? 0 : s.getInstalled(), s == null ? 0 : s.getOffered(), x.getPrevious() == null ? null : x.getPrevious().getId());
        }).toList());
    }

    private volatile Sys sys;
    private volatile long sysAt;

    /** 内存每次请求都不一样：记 5 分钟，看板后台刷新时这张卡片不会每次都变，整页也就不用跟着重画 */
    private Sys system() {
        Sys cached = sys;
        if (cached != null && System.currentTimeMillis() - sysAt < 5 * 60_000) return cached;
        sys = readSystem();
        sysAt = System.currentTimeMillis();
        return sys;
    }

    private Sys readSystem() {
        BuildProperties b = build.getIfAvailable();
        Runtime rt = Runtime.getRuntime();
        long memUsed = rt.totalMemory() - rt.freeMemory(), memMax = rt.maxMemory();
        long diskFree = install.freeDiskBytes(), diskTotal = install.dataDir().toFile().getTotalSpace();
        long diskUsed = diskFree < 0 || diskTotal <= 0 ? -1 : diskTotal - diskFree;
        String db = install.effectiveDatabase();
        return new Sys(b != null ? b.getVersion() : "开发版",
                AdminSettingsController.uptime(java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime()),
                memMax <= 0 ? 0 : (int) Math.round(memUsed * 100.0 / memMax),
                AdminSettingsController.size(memUsed) + " / " + AdminSettingsController.size(memMax),
                diskUsed < 0 ? 0 : (int) Math.round(diskUsed * 100.0 / diskTotal),
                diskFree < 0 ? "—" : "剩 " + AdminSettingsController.size(diskFree) + " / 共 " + AdminSettingsController.size(diskTotal),
                diskFree >= 0 && diskFree < 2L * 1024 * 1024 * 1024,
                blobs.describe(), db.startsWith("内置数据库") ? "内置数据库" : db.replaceFirst("^jdbc:", ""));
    }

    private void loginFeed(Model model) {
        Map<Long, String> names = new HashMap<>();
        for (AdminUser u : admins.findAll()) names.put(u.getId(), u.getName());
        List<Login> out = new ArrayList<>();
        for (LoginRecord r : logins.findTop8ByOrderByAtDesc()) {
            out.add(new Login(r.getAdminId() == null ? r.getUsername() : names.getOrDefault(r.getAdminId(), r.getUsername()),
                    r.getDevice(), r.isMobile(), r.isSuccess(), r.getReason(), r.getAt()));
            if (out.size() == 6) break;
        }
        model.addAttribute("boardLogins", out);
    }

    private static int px(long v, long max, int height) {
        return max == 0 || v == 0 ? 0 : (int) Math.max(3, Math.round(v * (double) height / max));
    }

    private Map<Long, String> agentNames() {
        Map<Long, String> names = new LinkedHashMap<>();
        for (Agent a : agentRepo.findAllByOrderByCreatedAtDesc()) names.put(a.getId(), a.getName());
        return names;
    }

    private Map<String, String> adminNames() {
        Map<String, String> names = new HashMap<>();
        for (AdminUser u : admins.findAll()) names.put(u.getUsername(), u.getName());
        return names;
    }
}
