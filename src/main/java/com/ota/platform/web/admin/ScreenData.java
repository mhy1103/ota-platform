package com.ota.platform.web.admin;

import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.agent.AgentService;
import com.ota.platform.app.AppService;
import com.ota.platform.app.Channel;
import com.ota.platform.app.ChannelRepository;
import com.ota.platform.app.OtaApp;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.codes.ActivationRecord;
import com.ota.platform.codes.ActivationRecordRepo;
import com.ota.platform.ota.LaunchErrorRepo;
import com.ota.platform.ota.ReleaseStat;
import com.ota.platform.ota.ReleaseStatsService;
import com.ota.platform.release.DeployEvent;
import com.ota.platform.release.DeployEventRepository;
import com.ota.platform.release.Deployment;
import com.ota.platform.release.DeploymentRepository;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseRepository;
import com.ota.platform.stats.DeviceDaily;
import com.ota.platform.stats.DeviceDailyRepo;
import com.ota.platform.stats.DeviceSeenRepository;
import com.ota.platform.stats.StatsService;
import com.ota.platform.web.AdminUi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * 数据大屏的数据：一个 App，或者当前管理员能看到的全部 App 合在一起。
 * <p>
 * 大屏每 10 秒取一次，App 可能有几十个，所以每个数都是一条按 App 分组的查询（app_id in (...) 加时间范围，走 App + 时间的索引），
 * 查询条数和 App 个数无关。数据分三档：今天的数（激活、设备、动态）每次都重新查，几个人同时开着大屏 3 秒内只算一次；
 * 近 30 天的设备构成、口令总数、热门口令这些变得慢的 1 分钟算一次；以前每天的激活数（过去的日子不会再变）5 分钟算一次。
 */
@Component
public class ScreenData {

    private static final Logger log = LoggerFactory.getLogger(ScreenData.class);

    /** 趋势图的天数（再往前同样天数用来对比） */
    private static final int DAYS = 30;
    private static final int SPAN = DAYS * 2;
    /** 活跃设备柱状图的天数 */
    private static final int DEV_DAYS = 14;
    /** App 排行里迷你趋势线的天数 */
    private static final int SPARK = 14;
    /** 全部 App 的排行最多带这么多个（今天激活多的在前）；页面上放不下的合成「其余 N 个 App」一行 */
    private static final int MAX_APPS = 500;
    private static final long FRESH_MS = 3_000, PICKS_MS = 20_000, SLOW_MS = 60_000, HISTORY_MS = 300_000;
    private static final int MAX_CACHED = 400;
    private static final Long NOBODY = -1L;

    /** 大屏看的范围：single 不为空是一个 App，否则是 apps 里的全部 */
    public record Scope(List<OtaApp> apps, OtaApp single) {
        public static Scope of(OtaApp app) {
            return new Scope(List.of(app), app);
        }

        public static Scope all(List<OtaApp> apps) {
            return new Scope(List.copyOf(apps), null);
        }

        public boolean all() {
            return single == null;
        }

        List<Long> ids() {
            return apps.stream().map(OtaApp::getId).sorted().toList();
        }

        String key() {
            return all() ? "all:" + join(ids()) : "app:" + single.getId();
        }
    }

    /** 一个渠道上的线上版本：灰度比例、装上 / 下发、启动失败率 */
    public record Lane(String channel, String runtime, String release, String message, int rollout, boolean embedded,
                       long installed, long offered, int failRate, boolean alarm) {}

    /** 更新覆盖的一类：latest 渠道上的线上版本、older 旧的热更新、embedded 还是安装包内置的、unknown 没上报或版本已删除 */
    public record Cover(String key, long count) {}

    /** 代理排行的一行：今天和近 7 天的有效激活（只算大屏范围里的 App） */
    public record Rank(Long id, String name, long today, long week, boolean off) {}

    /** 近 7 天用得最多的口令 */
    public record HotCode(String code, String agent, long count, int used, int max) {}

    /** 最新动态：激活（confirm / submit）和发布（deploy / rollout / rollback / embedded）；看全部 App 时 app 是 App 名称 */
    public record Event(String kind, String app, String main, String sub, int rollout, Instant at) {}

    /**
     * App 排行的一行。live：B 面（上线中）；vs：今天比昨天同一时刻；spark：近 14 天每天的有效激活；
     * cover：近 30 天活跃的设备里有多少在运行线上版本（百分比，没有设备是 -1）；errors：近 24 小时启动失败
     */
    public record AppRow(String slug, String name, Long id, String icon, boolean live, long today, AdminController.Delta vs,
                         long week, long[] spark, long devToday, long devMonth, int cover, long errors) {}

    /** 切换 App 的列表里的一项 */
    public record Pick(String slug, String name, Long id, String icon, boolean live, long today) {}

    private record Cached<T>(long at, T value) {}

    /** 前天及以前每天的有效激活（每个 App 一行，共 SPAN - 2 天）、这之前的累计、近 7 天里今天以前提交的次数 */
    private record History(Map<Long, long[]> daily, Map<Long, Long> before, long submitted) {}

    /** 变得慢的数 */
    private record Slow(Map<LocalDate, long[]> devDaily, boolean yesterdayKnown,
                        Map<Long, Long> active30, Map<Long, Long> latest30, long ios30, long android30, long dev7, List<Cover> cover,
                        List<AdminDevicesController.Share> runtimes, List<AdminDevicesController.Share> channels,
                        Map<Long, Long> errors, long codeTotal, long codeUsable, List<HotCode> hot, long codeActs7, long codesUsed7,
                        Map<Long, String> agentNames, Set<Long> agentOff, long agentEnabled, Map<Long, Long> agentWeekBefore) {}

    /** 每个 App 近 SPAN 天每天的有效激活（最后一格是今天）、昨天到这个钟点为止的、今天每个口令的 */
    private record Acts(Map<Long, long[]> daily, Map<Long, Long> same, Map<Long, Long> todayByCode) {}

    private final AdminUi ui;
    private final DataSource dataSource;
    private final ActivationRecordRepo recordRepo;
    private final ActivationCodeRepo codeRepo;
    private final DeviceSeenRepository devicesSeen;
    private final DeviceDailyRepo deviceDaily;
    private final ChannelRepository channels;
    private final DeploymentRepository deployments;
    private final ReleaseRepository releaseRepo;
    private final DeployEventRepository deployEvents;
    private final LaunchErrorRepo launchErrors;
    private final ReleaseViews releaseViews;
    private final ReleaseStatsService releaseStats;
    private final AgentRepo agentRepo;
    private final AgentService agentService;

    private final Map<String, Cached<Map<String, Object>>> fresh = new ConcurrentHashMap<>();
    private final Map<String, Cached<Slow>> slow = new ConcurrentHashMap<>();
    private final Map<String, Cached<History>> history = new ConcurrentHashMap<>();
    private final Map<String, Cached<Map<Long, Long>>> picked = new ConcurrentHashMap<>();
    /** 口令属于哪个代理（没有代理是 NOBODY）；1 分钟清一次，改了口令的代理最多晚 1 分钟 */
    private final Map<Long, Long> agentOfCode = new ConcurrentHashMap<>();
    private volatile long agentOfCodeAt;
    /** 按钟点合并的查询能不能用（个别数据库不认这种写法时改成逐条统计） */
    private volatile boolean hourly = true;
    private volatile Boolean oneByOne;

    public ScreenData(AdminUi ui, DataSource dataSource, ActivationRecordRepo recordRepo, ActivationCodeRepo codeRepo,
                      DeviceSeenRepository devicesSeen, DeviceDailyRepo deviceDaily, ChannelRepository channels,
                      DeploymentRepository deployments, ReleaseRepository releaseRepo, DeployEventRepository deployEvents,
                      LaunchErrorRepo launchErrors, ReleaseViews releaseViews, ReleaseStatsService releaseStats,
                      AgentRepo agentRepo, AgentService agentService) {
        this.ui = ui;
        this.dataSource = dataSource;
        this.recordRepo = recordRepo;
        this.codeRepo = codeRepo;
        this.devicesSeen = devicesSeen;
        this.deviceDaily = deviceDaily;
        this.channels = channels;
        this.deployments = deployments;
        this.releaseRepo = releaseRepo;
        this.deployEvents = deployEvents;
        this.launchErrors = launchErrors;
        this.releaseViews = releaseViews;
        this.releaseStats = releaseStats;
        this.agentRepo = agentRepo;
        this.agentService = agentService;
    }

    /**
     * 这个范围的大屏数据（页面模型里 sc 开头的那些）。codes：能不能看口令内容和热门口令；agents：能不能看代理排行。
     * 返回的内容不能改：几个人同时看同一个范围时拿到的是同一份
     */
    public Map<String, Object> snapshot(Scope scope, boolean codes, boolean agents) {
        String key = scope.key() + (codes ? "|c" : "|-") + (agents ? "|a" : "|-") + "|" + ui.today();
        Cached<Map<String, Object>> hit = fresh.get(key);
        if (hit != null && System.currentTimeMillis() - hit.at() < FRESH_MS) return hit.value();
        Map<String, Object> built = build(scope, codes, agents);
        trim(fresh, FRESH_MS);
        fresh.put(key, new Cached<>(System.currentTimeMillis(), built));
        return built;
    }

    /** 切换 App 的列表：能看到的每个 App 和它今天的有效激活，顺序和侧栏里一样 */
    public List<Pick> picks(List<OtaApp> visible) {
        if (visible.isEmpty()) return List.of();
        List<Long> ids = visible.stream().map(OtaApp::getId).sorted().toList();
        String key = join(ids) + "|" + ui.today();
        Cached<Map<Long, Long>> hit = picked.get(key);
        Map<Long, Long> today;
        if (hit != null && System.currentTimeMillis() - hit.at() < PICKS_MS) {
            today = hit.value();
        } else {
            today = new HashMap<>();
            LocalDateTime start = ui.startOfToday();
            for (Object[] r : each(ids, part -> recordRepo.countConfirmedByApp(part, start))) today.put((Long) r[0], ((Number) r[1]).longValue());
            trim(picked, PICKS_MS);
            picked.put(key, new Cached<>(System.currentTimeMillis(), today));
        }
        List<Pick> out = new ArrayList<>(visible.size());
        for (OtaApp a : visible) {
            out.add(new Pick(a.getSlug(), a.getName(), a.getId(), a.getIconVersion(), a.isRedeemOpen(), today.getOrDefault(a.getId(), 0L)));
        }
        return out;
    }

    private Map<String, Object> build(Scope scope, boolean codes, boolean agents) {
        ZoneId zone = ZoneId.of(ui.zoneId());
        ZonedDateTime now = ZonedDateTime.now(zone);
        LocalDate today = now.toLocalDate();
        List<Long> ids = scope.ids();
        History past = history(scope.key(), ids, today, zone);
        Slow slowly = slow(scope, ids, today, codes, agents);
        Map<String, Object> m = new LinkedHashMap<>();
        Acts acts = activations(m, ids, past, slowly, now, zone);
        Map<Long, Long> devToday = devices(m, ids, slowly, today, zone);
        m.put("scCodeTotal", slowly.codeTotal());
        m.put("scCodeUsable", slowly.codeUsable());
        m.put("scErrors", slowly.errors().values().stream().mapToLong(Long::longValue).sum());
        if (agents) agents(m, acts, slowly);
        if (codes) {
            m.put("scHotCodes", slowly.hot());
            m.put("scCodeActs7", slowly.codeActs7());
            m.put("scCodesUsed7", slowly.codesUsed7());
        }
        m.put("scEvents", events(scope, ids, codes));
        if (scope.all()) {
            apps(m, scope, acts, devToday, slowly);
        } else {
            lanes(m, scope.single());
            m.put("scRuntimes", slowly.runtimes());
            m.put("scChannels", slowly.channels());
        }
        return Collections.unmodifiableMap(m);
    }

    /**
     * 按 App 过滤再加时间范围的查询。MySQL 这类数据库会把「app_id in (...) and 时间 >= ?」一起走索引，一条查询就够；
     * H2 遇到多个 App 时只用 app_id 走索引、时间靠逐行过滤（等于把这些 App 的记录全扫一遍），所以在 H2 上逐个 App 查（每次都是索引上的一小段），结果合在一起
     */
    private <T> List<T> each(List<Long> ids, Function<Collection<Long>, List<T>> query) {
        if (ids.size() < 2 || !oneByOne()) return query.apply(ids);
        List<T> out = new ArrayList<>();
        for (Long id : ids) out.addAll(query.apply(List.of(id)));
        return out;
    }

    private boolean oneByOne() {
        Boolean known = oneByOne;
        if (known != null) return known;
        boolean h2 = false;
        try (Connection c = dataSource.getConnection()) {
            h2 = "H2".equalsIgnoreCase(c.getMetaData().getDatabaseProductName());
        } catch (Exception e) {
            log.debug("看不出是什么数据库，按一条查询管多个 App 的方式查：{}", e.toString());
        }
        oneByOne = h2;
        return h2;
    }

    // ==================== 有效激活 ====================

    /** 近 30 天每天（和再往前 30 天比）、今天和昨天每小时、今天比昨天同一时刻、今天提交了多少次、用到几个口令 */
    private Acts activations(Map<String, Object> m, List<Long> ids, History past, Slow slowly, ZonedDateTime now, ZoneId zone) {
        LocalDate today = now.toLocalDate(), yesterday = today.minusDays(1);
        LocalTime nowTime = now.toLocalTime();
        ZoneId sys = ZoneId.systemDefault();
        Map<Long, long[]> daily = new HashMap<>();
        for (Long id : ids) {
            long[] d = new long[SPAN];
            long[] before = past.daily().get(id);
            if (before != null) System.arraycopy(before, 0, d, 0, SPAN - 2);
            daily.put(id, d);
        }
        Map<Long, Long> same = new HashMap<>(), todayByCode = new HashMap<>();
        long[] hours = new long[24], hoursY = new long[24];
        LocalDateTime sinceYesterday = ui.startOf(yesterday);
        for (Object[] r : each(ids, part -> recordRepo.confirmedTimesIn(part, sinceYesterday))) {
            long[] d = daily.get((Long) r[0]);
            if (d == null || r[1] == null) continue;
            ZonedDateTime z = ((LocalDateTime) r[1]).atZone(sys).withZoneSameInstant(zone);
            LocalDate day = z.toLocalDate();
            if (day.equals(today)) {
                d[SPAN - 1]++;
                hours[z.getHour()]++;
                if (r[2] != null) todayByCode.merge((Long) r[2], 1L, Long::sum);
            } else if (day.equals(yesterday)) {
                d[SPAN - 2]++;
                hoursY[z.getHour()]++;
                if (!z.toLocalTime().isAfter(nowTime)) same.merge((Long) r[0], 1L, Long::sum);
            }
        }
        long[] total = new long[SPAN];
        for (long[] d : daily.values()) for (int i = 0; i < SPAN; i++) total[i] += d[i];
        long sameTime = same.values().stream().mapToLong(Long::longValue).sum();
        long before = past.before().values().stream().mapToLong(Long::longValue).sum();
        // 今天提交的：按口令合计，加起来是提交次数，口令个数是今天用到的口令
        Set<Long> usedCodes = new HashSet<>();
        long submitted = 0;
        LocalDateTime sinceToday = ui.startOf(today);
        for (Object[] r : each(ids, part -> recordRepo.submittedByCode(part, sinceToday))) {
            usedCodes.add((Long) r[0]);
            submitted += ((Number) r[1]).longValue();
        }
        // 待确认：今天提交、到现在还没确认的（以前提交没确认的不算）
        long pending = 0;
        for (Long n : each(ids, part -> List.of(recordRepo.countPendingSince(part, sinceToday)))) pending += n;

        DateTimeFormatter md = DateTimeFormatter.ofPattern("MM-dd");
        List<String> labels = new ArrayList<>();
        for (int i = DAYS - 1; i >= 0; i--) labels.add(i == 0 ? "今天" : md.format(today.minusDays(i)));
        int n = SPAN;
        m.put("scDays", labels);
        m.put("scTrend", Arrays.copyOfRange(total, DAYS, n));
        m.put("scTrendPrev", Arrays.copyOfRange(total, 0, DAYS));
        m.put("scHours", hours);
        m.put("scHoursY", hoursY);
        m.put("scHour", now.getHour());
        m.put("scToday", total[n - 1]);
        m.put("scYesterday", total[n - 2]);
        m.put("scSameTime", sameTime);
        m.put("scTodayVs", AdminController.delta(total[n - 1], sameTime));
        long week = sum(total, n - 7, n), month = sum(total, DAYS, n);
        m.put("scWeek", week);
        m.put("scWeekVs", AdminController.delta(week, sum(total, n - 14, n - 7)));
        m.put("scMonth", month);
        m.put("scMonthVs", AdminController.delta(month, sum(total, 0, DAYS)));
        m.put("scTotal", before + total[n - 2] + total[n - 1]);
        m.put("scPending", pending);
        m.put("scSubmitted", submitted);
        m.put("scSub7", past.submitted() + submitted);
        m.put("scCodeUsedToday", usedCodes.size());
        return new Acts(daily, same, todayByCode);
    }

    /** 前天及以前每天的有效激活。过去的日子不会再变，算一次用 5 分钟 */
    private History history(String scopeKey, List<Long> ids, LocalDate today, ZoneId zone) {
        String key = scopeKey + "|" + today;
        Cached<History> hit = history.get(key);
        if (hit != null && System.currentTimeMillis() - hit.at() < HISTORY_MS) return hit.value();
        LocalDate first = today.minusDays(SPAN - 1);
        LocalDateTime from = ui.startOf(first), to = ui.startOf(today.minusDays(1));
        ZoneId sys = ZoneId.systemDefault();
        Map<Long, long[]> daily = new HashMap<>();
        boolean done = false;
        // 展示时区和服务器时区差整点时，让数据库按钟点合并好再取：行数最多是 App 数 × 天数 × 24，和激活记录有多少条无关
        if (hourly && aligned(sys, zone, from, to)) {
            try {
                for (Object[] r : each(ids, part -> recordRepo.confirmedByHour(part, from, to))) {
                    LocalDateTime t = LocalDateTime.of(number(r[1]), number(r[2]), number(r[3]), number(r[4]), 0);
                    add(daily, (Long) r[0], t, ((Number) r[5]).longValue(), sys, zone, first);
                }
                done = true;
            } catch (RuntimeException e) {
                hourly = false;
                daily.clear();
                log.warn("按钟点合并激活数的查询在这个数据库上不能用，改成逐条统计：{}", e.toString());
            }
        }
        if (!done) {
            for (Object[] r : each(ids, part -> recordRepo.confirmedTimesBetween(part, from, to))) {
                if (r[1] != null) add(daily, (Long) r[0], (LocalDateTime) r[1], 1, sys, zone, first);
            }
        }
        Map<Long, Long> before = new HashMap<>();
        for (Object[] r : recordRepo.countConfirmedBefore(ids, to)) before.put((Long) r[0], ((Number) r[1]).longValue());
        long submitted = recordRepo.countSubmittedBetween(ids, ui.startOf(today.minusDays(6)), ui.startOf(today));
        History built = new History(daily, before, submitted);
        trim(history, HISTORY_MS);
        history.put(key, new Cached<>(System.currentTimeMillis(), built));
        return built;
    }

    private static void add(Map<Long, long[]> daily, Long app, LocalDateTime t, long n, ZoneId sys, ZoneId zone, LocalDate first) {
        int i = (int) ChronoUnit.DAYS.between(first, t.atZone(sys).withZoneSameInstant(zone).toLocalDate());
        if (i < 0 || i >= SPAN - 2) return;
        daily.computeIfAbsent(app, k -> new long[SPAN - 2])[i] += n;
    }

    /** 这段时间两头，展示时区和服务器时区差的都是整点 */
    private static boolean aligned(ZoneId sys, ZoneId zone, LocalDateTime from, LocalDateTime to) {
        for (LocalDateTime t : List.of(from, to)) {
            Instant i = t.atZone(sys).toInstant();
            if ((zone.getRules().getOffset(i).getTotalSeconds() - sys.getRules().getOffset(i).getTotalSeconds()) % 3600 != 0) return false;
        }
        return true;
    }

    // ==================== 设备 ====================

    /** 近 14 天每天的活跃（分平台）和新设备、近 7 / 30 天活跃、平台构成、更新覆盖；返回每个 App 今天的活跃设备 */
    private Map<Long, Long> devices(Map<String, Object> m, List<Long> ids, Slow slowly, LocalDate today, ZoneId zone) {
        Map<Long, Long> byApp = new HashMap<>();
        long active = 0, fresh = 0, ios = 0, android = 0;
        Instant start = today.atStartOfDay(zone).toInstant();
        for (Object[] r : each(ids, part -> devicesSeen.activeByAppPlatform(part, start))) {
            long n = ((Number) r[2]).longValue();
            byApp.merge((Long) r[0], n, Long::sum);
            active += n;
            fresh += r[3] == null ? 0 : ((Number) r[3]).longValue();
            if ("ios".equalsIgnoreCase((String) r[1])) ios += n;
            else if ("android".equalsIgnoreCase((String) r[1])) android += n;
        }
        DateTimeFormatter md = DateTimeFormatter.ofPattern("MM-dd");
        long[] iosDays = new long[DEV_DAYS], androidDays = new long[DEV_DAYS], otherDays = new long[DEV_DAYS], freshDays = new long[DEV_DAYS];
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < DEV_DAYS; i++) {
            boolean last = i == DEV_DAYS - 1;
            LocalDate day = today.minusDays(DEV_DAYS - 1 - i);
            labels.add(last ? "今天" : md.format(day));
            // [活跃, 新设备, iOS, Android]：今天的直接按设备表算，以前的用每天记下的数
            long[] a = last ? new long[]{active, fresh, ios, android} : slowly.devDaily().get(day);
            if (a == null) continue;
            iosDays[i] = a[2];
            androidDays[i] = a[3];
            otherDays[i] = Math.max(0, a[0] - a[2] - a[3]);
            freshDays[i] = a[1];
        }
        long[] yesterday = slowly.devDaily().get(today.minusDays(1));
        long month = slowly.active30().values().stream().mapToLong(Long::longValue).sum();
        m.put("scDevDays", labels);
        m.put("scDevIos", iosDays);
        m.put("scDevAndroid", androidDays);
        m.put("scDevOther", otherDays);
        m.put("scDevFresh", freshDays);
        m.put("scDevToday", active);
        m.put("scDevNew", fresh);
        m.put("scDevVs", slowly.yesterdayKnown() && yesterday != null ? AdminController.delta(active, yesterday[0]) : null);
        m.put("scDevWeek", slowly.dev7());
        // 今天刚出现的设备可能还没算进 1 分钟一算的近 30 天里：不让它比今天的还少
        m.put("scDevMonth", Math.max(month, active));
        m.put("scIos", slowly.ios30());
        m.put("scAndroid", slowly.android30());
        m.put("scCover", slowly.cover());
        return byApp;
    }

    // ==================== 变得慢的数 ====================

    private Slow slow(Scope scope, List<Long> ids, LocalDate today, boolean codes, boolean agents) {
        String key = scope.key() + (codes ? "|c" : "|-") + (agents ? "|a" : "|-") + "|" + today;
        Cached<Slow> hit = slow.get(key);
        if (hit != null && System.currentTimeMillis() - hit.at() < SLOW_MS) return hit.value();

        // 以前每天的设备数（今天的每次直接按设备表算）
        Map<LocalDate, long[]> devDaily = new HashMap<>();
        LocalDate yesterday = today.minusDays(1);
        boolean yesterdayKnown = false;
        for (DeviceDaily d : deviceDaily.findByAppIdInAndDayGreaterThanEqual(ids, today.minusDays(DEV_DAYS - 1))) {
            if (!d.getDay().isBefore(today)) continue;
            long[] a = devDaily.computeIfAbsent(d.getDay(), k -> new long[4]);
            a[0] += d.getActive();
            a[1] += d.getNewDevices();
            a[2] += d.getIos();
            a[3] += d.getAndroid();
            if (d.getDay().equals(yesterday)) yesterdayKnown = true;
        }

        // 近 30 天检查过更新的设备：平台、正在运行的是不是线上版本
        Instant month = Instant.now().minus(StatsService.ACTIVE_WINDOW);
        Map<Long, Set<String>> live = liveIds(ids);
        Set<String> known = scope.all() ? null : new HashSet<>(releaseRepo.idsOf(scope.single().getId()));
        Map<Long, Long> active30 = new HashMap<>(), latest30 = new HashMap<>();
        long ios = 0, android = 0, latest = 0, older = 0, embedded = 0, unknown = 0;
        for (Object[] r : devicesSeen.countByAppPlatformUpdate(ids, month)) {
            Long app = (Long) r[0];
            String update = (String) r[2];
            long n = ((Number) r[3]).longValue();
            active30.merge(app, n, Long::sum);
            if ("ios".equalsIgnoreCase((String) r[1])) ios += n;
            else if ("android".equalsIgnoreCase((String) r[1])) android += n;
            if (update != null && live.getOrDefault(app, Set.of()).contains(update)) {
                latest += n;
                latest30.merge(app, n, Long::sum);
            } else if (StatsService.EMBEDDED.equals(update)) {
                embedded += n;
            } else if (update == null || update.isEmpty() || (known != null && !known.contains(update))) {
                unknown += n;
            } else {
                older += n;
            }
        }
        List<Cover> cover = List.of(new Cover("latest", latest), new Cover("older", older), new Cover("embedded", embedded), new Cover("unknown", unknown));
        long dev7 = devicesSeen.countActiveIn(ids, Instant.now().minus(Duration.ofDays(7)));
        List<AdminDevicesController.Share> runtimes = null, channelShares = null;
        if (!scope.all()) {
            Long appId = scope.single().getId();
            runtimes = AdminDevicesController.shares(devicesSeen.countByRuntime(appId, month), "未上报");
            channelShares = AdminDevicesController.shares(devicesSeen.countByChannel(appId, month), AppService.DEFAULT_CHANNEL);
        }
        Map<Long, Long> errors = new HashMap<>();
        for (Object[] r : launchErrors.countByAppSince(ids, Instant.now().minus(Duration.ofDays(1)))) errors.put((Long) r[0], ((Number) r[1]).longValue());

        // 代理的名字；近 7 天里今天以前的有效激活（今天的每次重新查）
        Map<Long, String> agentNames = new LinkedHashMap<>();
        Set<Long> agentOff = new HashSet<>();
        Map<Long, Long> agentWeekBefore = new HashMap<>();
        long agentEnabled = 0;
        if (codes || agents) {
            for (Agent a : agentRepo.findAllByOrderByCreatedAtDesc()) {
                agentNames.put(a.getId(), a.getName());
                // 二级代理是代理自己开的，口令和激活都算在上级名下
                if (a.isSub()) continue;
                if (Boolean.TRUE.equals(a.getEnabled())) agentEnabled++;
                else agentOff.add(a.getId());
            }
        }
        if (agents) {
            LocalDateTime from = ui.startOf(today.minusDays(6)), to = ui.startOf(today);
            for (Object[] r : each(ids, part -> recordRepo.confirmedByAgent(part, from, to))) {
                agentWeekBefore.merge((Long) r[0], ((Number) r[1]).longValue(), Long::sum);
            }
        }

        // 近 7 天用得最多的口令
        List<HotCode> hot = List.of();
        long codeActs7 = 0, codesUsed7 = 0;
        if (codes) {
            LocalDateTime since = LocalDateTime.now().minusDays(7);
            List<Object[]> rows = recordRepo.topCodesIn(ids, since, PageRequest.of(0, 12));
            Map<Long, ActivationCode> byId = new HashMap<>();
            codeRepo.findAllById(rows.stream().map(r -> (Long) r[0]).toList()).forEach(c -> byId.put(c.getId(), c));
            List<HotCode> list = new ArrayList<>();
            for (Object[] r : rows) {
                ActivationCode c = byId.get((Long) r[0]);
                list.add(new HotCode((String) r[1], c == null || c.getAgentId() == null ? null : agentNames.get(c.getAgentId()),
                        ((Number) r[2]).longValue(), c == null ? 0 : c.limitUsed(), c == null ? 0 : c.getMaxUses()));
            }
            hot = list;
            for (Object[] u : recordRepo.codeUsageIn(ids, since)) {
                codeActs7 = u[0] == null ? 0 : ((Number) u[0]).longValue();
                codesUsed7 = u[1] == null ? 0 : ((Number) u[1]).longValue();
            }
        }

        Slow built = new Slow(devDaily, yesterdayKnown, active30, latest30, ios, android, dev7, cover,
                runtimes, channelShares, errors, codeRepo.count(), codeRepo.count(AdminController.codeSpec("", "usable", "")),
                hot, codeActs7, codesUsed7, agentNames, agentOff, agentEnabled, agentWeekBefore);
        trim(slow, SLOW_MS);
        slow.put(key, new Cached<>(System.currentTimeMillis(), built));
        return built;
    }

    /** 每个 App 现在线上的版本（各渠道、各 Runtime 的），回退到内置版本的不算 */
    private Map<Long, Set<String>> liveIds(List<Long> ids) {
        Map<Long, Long> appOfChannel = new HashMap<>();
        for (Channel c : channels.findByAppIdIn(ids)) appOfChannel.put(c.getId(), c.getAppId());
        Map<Long, Set<String>> out = new HashMap<>();
        if (appOfChannel.isEmpty()) return out;
        for (Deployment d : deployments.findByChannelIdIn(appOfChannel.keySet())) {
            if (d.isRollbackToEmbedded() || d.getReleaseId() == null) continue;
            out.computeIfAbsent(appOfChannel.get(d.getChannelId()), k -> new HashSet<>()).add(d.getReleaseId());
        }
        return out;
    }

    // ==================== 代理、动态、发布、App 排行 ====================

    /** 代理按今天的有效激活排，一样多再按近 7 天；今天没有激活的不进排行（不拿来补位） */
    private void agents(Map<String, Object> m, Acts acts, Slow slowly) {
        Map<Long, Long> owner = agentsOf(acts.todayByCode().keySet());
        Map<Long, Long> todayBy = new HashMap<>();
        acts.todayByCode().forEach((code, n) -> {
            Long agent = owner.get(code);
            if (agent != null && !NOBODY.equals(agent)) todayBy.merge(agent, n, Long::sum);
        });
        List<Rank> ranks = new ArrayList<>();
        long sumToday = 0, sumWeek = 0, active = 0;
        for (Map.Entry<Long, String> e : slowly.agentNames().entrySet()) {
            long t = todayBy.getOrDefault(e.getKey(), 0L), w = slowly.agentWeekBefore().getOrDefault(e.getKey(), 0L) + t;
            sumToday += t;
            sumWeek += w;
            if (t <= 0) continue;
            active++;
            ranks.add(new Rank(e.getKey(), e.getValue(), t, w, slowly.agentOff().contains(e.getKey())));
        }
        ranks.sort(Comparator.comparingLong(Rank::today).reversed()
                .thenComparing(Comparator.comparingLong(Rank::week).reversed())
                .thenComparing(Rank::name));
        m.put("scAgentCount", slowly.agentEnabled());
        m.put("scAgentActive", active);
        m.put("scRequests", agentService.pendingCount());
        m.put("scAgentToday", sumToday);
        m.put("scAgentWeek", sumWeek);
        m.put("scRanks", ranks.size() > 30 ? List.copyOf(ranks.subList(0, 30)) : ranks);
    }

    /** 这些口令各属于哪个代理（没查过的一次查出来，记 1 分钟） */
    private Map<Long, Long> agentsOf(Collection<Long> codeIds) {
        long now = System.currentTimeMillis();
        if (now - agentOfCodeAt > SLOW_MS || agentOfCode.size() > 200_000) {
            agentOfCode.clear();
            agentOfCodeAt = now;
        }
        List<Long> missing = codeIds.stream().filter(id -> !agentOfCode.containsKey(id)).toList();
        for (int i = 0; i < missing.size(); i += 500) {
            List<Long> chunk = missing.subList(i, Math.min(missing.size(), i + 500));
            for (Object[] r : codeRepo.agentsOf(chunk)) agentOfCode.put((Long) r[0], r[1] == null ? NOBODY : (Long) r[1]);
            // 口令已经删掉的也记下，免得每次都去查
            for (Long id : chunk) agentOfCode.putIfAbsent(id, NOBODY);
        }
        return agentOfCode;
    }

    /** 最近的激活和发布合在一起按时间排；没有口令权限的看不到口令内容 */
    private List<Event> events(Scope scope, List<Long> ids, boolean codes) {
        Map<Long, String> names = new HashMap<>();
        if (scope.all()) for (OtaApp a : scope.apps()) names.put(a.getId(), a.getName());
        // 先只看近一天的（走 App + 时间的索引，量再大也快）；不够再往前找
        List<ActivationRecord> recent = recent(ids, LocalDateTime.now().minusDays(1));
        if (recent.size() < 8) {
            recent = scope.all() ? recent(ids, LocalDateTime.now().minusDays(7))
                    : recordRepo.findTop8ByAppIdOrderByActivatedAtDesc(scope.single().getId());
        }
        List<Event> out = new ArrayList<>();
        ZoneId sys = ZoneId.systemDefault();
        for (ActivationRecord r : recent) {
            boolean ok = Boolean.TRUE.equals(r.getConfirmed());
            LocalDateTime t = ok && r.getConfirmedAt() != null ? r.getConfirmedAt() : r.getActivatedAt();
            if (t == null) continue;
            String device = r.getDeviceId() == null ? null
                    : r.getDeviceId().substring(Math.max(0, r.getDeviceId().length() - 6)).toUpperCase(Locale.ROOT);
            out.add(new Event(ok ? "confirm" : "submit", names.get(r.getAppId()), codes ? r.getCodeText() : null, device, 0, t.atZone(sys).toInstant()));
        }
        for (DeployEvent e : deployEvents.recentIn(ids, PageRequest.of(0, 8))) {
            out.add(new Event(e.getAction().toLowerCase(Locale.ROOT), names.get(e.getAppId()), e.getChannel(),
                    e.getReleaseId() == null ? null : ui.rid(e.getReleaseId()), e.getRollout(), e.getAt()));
        }
        out.sort(Comparator.comparing(Event::at).reversed());
        return out.size() > 12 ? List.copyOf(out.subList(0, 12)) : out;
    }

    /** since 之后最近提交的 10 条 */
    private List<ActivationRecord> recent(List<Long> ids, LocalDateTime since) {
        List<ActivationRecord> rows = each(ids, part -> recordRepo.recentIn(part, since, PageRequest.of(0, 10)));
        if (rows.size() <= 10) return rows;
        List<ActivationRecord> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(ActivationRecord::getActivatedAt, Comparator.nullsFirst(Comparator.naturalOrder())).reversed());
        return sorted.subList(0, 10);
    }

    /** 各渠道的线上版本（最近更新的在前，最多 6 条） */
    private void lanes(Map<String, Object> m, OtaApp app) {
        List<ReleaseViews.Live> live = releaseViews.live(app);
        Map<String, ReleaseStat> st = releaseStats.get(live.stream().filter(l -> l.getRelease() != null)
                .map(l -> l.getRelease().getId()).distinct().toList());
        m.put("scLanes", live.stream()
                .sorted(Comparator.comparing(ReleaseViews.Live::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(6)
                .map(l -> {
                    Release r = l.getRelease();
                    ReleaseStat s = r == null ? null : st.get(r.getId());
                    return new Lane(l.getChannel(), l.getRuntime(), r == null ? null : r.shortId(), r == null ? null : r.getMessage(),
                            l.getRollout(), l.isRollbackToEmbedded(), s == null ? 0 : s.getInstalled(), s == null ? 0 : s.getOffered(),
                            s == null ? 0 : s.getFailRate(), ReleaseStatsService.alarming(s));
                }).toList());
    }

    /** 全部 App 的排行：今天有效激活多的在前，一样多再按近 7 天、今天的活跃设备 */
    private void apps(Map<String, Object> m, Scope scope, Acts acts, Map<Long, Long> devToday, Slow slowly) {
        List<AppRow> rows = new ArrayList<>();
        int live = 0, busy = 0;
        for (OtaApp a : scope.apps()) {
            long[] d = acts.daily().get(a.getId());
            long today = d[SPAN - 1], seenToday = devToday.getOrDefault(a.getId(), 0L);
            long month = Math.max(slowly.active30().getOrDefault(a.getId(), 0L), seenToday);
            if (a.isRedeemOpen()) live++;
            if (today > 0) busy++;
            rows.add(new AppRow(a.getSlug(), a.getName(), a.getId(), a.getIconVersion(), a.isRedeemOpen(), today,
                    AdminController.delta(today, acts.same().getOrDefault(a.getId(), 0L)), sum(d, SPAN - 7, SPAN),
                    Arrays.copyOfRange(d, SPAN - SPARK, SPAN), seenToday, month,
                    month > 0 ? com.ota.platform.common.Percent.of(slowly.latest30().getOrDefault(a.getId(), 0L), month) : -1,
                    slowly.errors().getOrDefault(a.getId(), 0L)));
        }
        rows.sort(Comparator.comparingLong(AppRow::today).reversed()
                .thenComparing(Comparator.comparingLong(AppRow::week).reversed())
                .thenComparing(Comparator.comparingLong(AppRow::devToday).reversed())
                .thenComparing(AppRow::name));
        m.put("scApps", rows.size() > MAX_APPS ? List.copyOf(rows.subList(0, MAX_APPS)) : rows);
        m.put("scAppCount", rows.size());
        m.put("scAppLive", live);
        m.put("scAppBusy", busy);
    }

    // ==================== 小工具 ====================

    private static long sum(long[] a, int from, int to) {
        long s = 0;
        for (int i = Math.max(0, from); i < to; i++) s += a[i];
        return s;
    }

    private static int number(Object o) {
        return ((Number) o).intValue();
    }

    private static String join(List<Long> ids) {
        StringBuilder sb = new StringBuilder();
        for (Long id : ids) sb.append(id).append(',');
        return sb.toString();
    }

    /** 缓存不会一直涨：过期的清掉，还是太多就全清 */
    private static <T> void trim(Map<String, Cached<T>> cache, long ttl) {
        if (cache.size() < MAX_CACHED) return;
        long now = System.currentTimeMillis();
        cache.values().removeIf(c -> now - c.at() >= ttl);
        if (cache.size() >= MAX_CACHED) cache.clear();
    }
}
