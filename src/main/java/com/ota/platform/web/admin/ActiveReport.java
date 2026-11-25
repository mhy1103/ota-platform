package com.ota.platform.web.admin;

import com.ota.platform.agent.Agent;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationDayRepo;
import com.ota.platform.codes.ActivationRecordRepo;
import com.ota.platform.web.AdminUi;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;

/**
 * 代理激活的设备的日活和留存（只给管理员看，按真实的激活算，不扣量）。
 * 日活：那天 App 启动时校验过授权、确认过激活的设备（见 ActiveDays）；新激活：那天确认的激活；
 * 留存：某天新激活的设备，第 N 天又打开了的占多少。看的范围可以是全部代理、一个代理、TA 的一个二级代理、一个口令
 */
@Component
public class ActiveReport {

    public static final List<Integer> RANGES = List.of(7, 14, 30, 60, 90);
    static final List<Integer> AGES = List.of(1, 3, 7, 14, 30);
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final String[] WEEK = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private static final int PLOT_PX = 150;

    private final ActivationDayRepo days;
    private final ActivationRecordRepo records;
    private final AdminUi ui;

    public ActiveReport(ActivationDayRepo days, ActivationRecordRepo records, AdminUi ui) {
        this.days = days;
        this.records = records;
        this.ui = ui;
    }

    /** 从哪天开始有日活记录 */
    public LocalDate since() {
        return days.firstDay();
    }

    /** 看的范围：kind 是 all / agent / sub / code；codes 是范围里的口令，all 是所有归了代理的 */
    public record Scope(String kind, List<ActivationCode> codes) {}

    /** 趋势图的一天：fresh 是那天新激活的（算在日活里，柱子顶上那一截），none 是那天还没开始统计 */
    public record Bar(String label, String tip, long dau, long fresh, int px, int freshPx, boolean last, boolean none) {}

    /** 留存表的一格：pct 为空时 state 说明为什么（wait 还没到那天、none 那天还没统计、zero 那天没有新激活）；live 是今天，到现在的数，不算进合计 */
    public record Cell(Integer pct, long n, String state, String tip) {}

    /** 留存表一行：那天新激活了多少台、之后第 1 / 3 / 7 / 14 / 30 天还在用的 */
    public record Cohort(String date, long size, List<Cell> cells) {}

    /** 明细表一行（一个代理、一个二级代理或一个口令） */
    public record Row(String name, String note, long total, long today, long yesterday, AdminController.Delta delta,
                      Integer d1, Integer d7, String href, boolean off) {}

    /** 一组口令的数：每天日活、每天新激活、每个激活日第几天还在用的 */
    static final class Agg {
        final Map<LocalDate, Long> dau = new HashMap<>();
        final Map<LocalDate, Long> fresh = new HashMap<>();
        final Map<LocalDate, Map<Integer, Long>> kept = new HashMap<>();
        long total;

        void add(Agg o) {
            o.dau.forEach((k, v) -> dau.merge(k, v, Long::sum));
            o.fresh.forEach((k, v) -> fresh.merge(k, v, Long::sum));
            o.kept.forEach((d, m) -> m.forEach((a, v) -> kept.computeIfAbsent(d, x -> new HashMap<>()).merge(a, v, Long::sum)));
            total += o.total;
        }

        long dau(LocalDate d) { return dau.getOrDefault(d, 0L); }
        long fresh(LocalDate d) { return fresh.getOrDefault(d, 0L); }
        long kept(LocalDate d, int age) { return kept.getOrDefault(d, Map.of()).getOrDefault(age, 0L); }
    }

    /** 算好的每个口令的数，拿来出明细表 */
    public record Data(Map<Long, ActivationCode> byId, Map<Long, Agg> perCode, LocalDate since, LocalDate today) {}

    /** 算好放进模型（指标、趋势、留存表），返回的数拿给 rows 出明细 */
    public Data fill(Model model, Scope scope, int range) {
        LocalDate today = ui.today();
        LocalDate from = today.minusDays(range - 1);
        LocalDate since = days.firstDay();
        LocalDate dataFrom = from.minusDays(1).isBefore(today.minusDays(14)) ? from.minusDays(1) : today.minusDays(14);
        Map<Long, ActivationCode> byId = new HashMap<>();
        for (ActivationCode c : scope.codes()) byId.put(c.getId(), c);
        Map<Long, Agg> perCode = perCode(scope, byId, dataFrom, today);

        Agg all = new Agg();
        perCode.values().forEach(all::add);

        model.addAttribute("actRange", range);
        model.addAttribute("actRanges", RANGES);
        model.addAttribute("actSince", since == null ? null : since.getYear() == today.getYear() ? MD.format(since) : since.toString());
        model.addAttribute("actStarted", since != null);
        long yesterday = all.dau(today.minusDays(1)), before = all.dau(today.minusDays(2));
        model.addAttribute("actToday", all.dau(today));
        model.addAttribute("actFreshToday", all.fresh(today));
        model.addAttribute("actYesterday", yesterday);
        model.addAttribute("actBefore", before);
        model.addAttribute("actDelta", counted(since, today.minusDays(2)) ? AdminController.delta(yesterday, before) : null);
        long week = 0, prevWeek = 0;
        int weekDays = 0, prevDays = 0;
        for (int i = 1; i <= 7; i++) {
            LocalDate d = today.minusDays(i), p = today.minusDays(i + 7);
            if (counted(since, d)) { week += all.dau(d); weekDays++; }
            if (counted(since, p)) { prevWeek += all.dau(p); prevDays++; }
        }
        long avg = weekDays == 0 ? 0 : Math.round(week / (double) weekDays), prevAvg = prevDays == 0 ? 0 : Math.round(prevWeek / (double) prevDays);
        model.addAttribute("actAvg", weekDays == 0 ? null : avg);
        model.addAttribute("actAvgDelta", weekDays == 0 || prevDays == 0 ? null : AdminController.delta(avg, prevAvg));
        model.addAttribute("actTotal", all.total);
        model.addAttribute("actRate", all.total == 0 || !counted(since, today.minusDays(1)) ? null : pct(yesterday, all.total));
        long[] d1 = retention(all, since, today, 1, 7), d7 = retention(all, since, today, 7, 7);
        model.addAttribute("actD1", d1[1] == 0 ? null : pct(d1[0], d1[1]));
        model.addAttribute("actD1Size", d1[1]);
        model.addAttribute("actD1Back", d1[0]);
        model.addAttribute("actD7", d7[1] == 0 ? null : pct(d7[0], d7[1]));
        model.addAttribute("actD7Size", d7[1]);
        model.addAttribute("actD7Back", d7[0]);
        model.addAttribute("actBars", bars(all, since, from, today, range));
        model.addAttribute("actCohorts", cohorts(all, since, today, Math.min(range, 30)));
        model.addAttribute("actAges", AGES);
        return new Data(byId, perCode, since, today);
    }

    /**
     * 明细表：groupOf 把口令分到哪一行（返回 null 不算），names 是每一行的名字、备注、链接、停用了没有（只列 names 里有的）。
     * 按昨天的日活从多到少排
     */
    public List<Row> rows(Data data, Function<ActivationCode, Object> groupOf, Map<Object, Row> names) {
        LocalDate today = data.today(), since = data.since();
        Map<Object, Agg> grouped = new LinkedHashMap<>();
        for (Object key : names.keySet()) grouped.put(key, new Agg());
        for (Map.Entry<Long, Agg> e : data.perCode().entrySet()) {
            Object key = groupOf.apply(data.byId().get(e.getKey()));
            if (key != null && grouped.containsKey(key)) grouped.get(key).add(e.getValue());
        }
        List<Row> rows = new ArrayList<>();
        grouped.forEach((key, a) -> {
            Row n = names.get(key);
            long y = a.dau(today.minusDays(1)), b = a.dau(today.minusDays(2));
            long[] r1 = retention(a, since, today, 1, 7), r7 = retention(a, since, today, 7, 7);
            rows.add(new Row(n.name(), n.note(), a.total, a.dau(today), y, counted(since, today.minusDays(2)) ? AdminController.delta(y, b) : null,
                    r1[1] == 0 ? null : pct(r1[0], r1[1]), r7[1] == 0 ? null : pct(r7[0], r7[1]), n.href(), n.off()));
        });
        rows.sort(Comparator.comparingLong(Row::yesterday).reversed().thenComparing(Comparator.comparingLong(Row::today).reversed())
                .thenComparing(Comparator.comparingLong(Row::total).reversed()).thenComparing(Row::name));
        return rows;
    }

    /** 明细表里一行的名字、备注、链接（数由 rows 算） */
    public static Row name(String name, String note, String href, boolean off) {
        return new Row(name, note, 0, 0, 0, null, null, null, href, off);
    }

    // ==================== 代理后台的留存（只给百分比，不给台数） ====================

    /** 第 1..7 天 */
    public static final List<Integer> DAYS7 = List.of(1, 2, 3, 4, 5, 6, 7);
    private static final List<Integer> AGES7 = List.of(0, 1, 2, 3, 4, 5, 6, 7);

    /** 留存表一格：pct 百分比（空时看 state：wait 还没到那天、none 那时还没统计、zero 那天没有新激活）；live 是今天，到现在的数，还会涨 */
    public record RetCell(Integer pct, String state) {}

    /** 留存表一行：date 是激活那天，cells 是第 1..7 天的留存 */
    public record RetRow(String date, boolean today, List<RetCell> cells) {}

    /** 一段时间的留存：rows 是每天一行（合计在最前），d1 / d7 是这段时间加起来的次日 / 7 日留存，matured 是满 7 天、算进 d7 的激活数 */
    public record Ret(List<RetRow> rows, Integer d1, Integer d7, long matured) {}

    /**
     * 代理的留存：from..to 之间每个激活日，新激活的设备第 1..7 天还打开的占多少（封顶 7 天）。
     * 只返回百分比，不含台数（不泄露扣量前的真实数）。第一行是这段时间加起来的合计，d1 / d7 也是合计，结算按 d7。
     * asOf 是代理能看到的数据到哪天（实时是今天，每日 0 点是今天但只看得到昨天以前的，手动更新是更新的那天），过了它的不显示；
     * 第 N 天正好是 asOf 的那天还没过完：live 时格子实时显示到现在的数，否则显示「…」；都不算进合计和 d1 / d7，不然结算的留存会偏低
     */
    public Ret retention7(List<ActivationCode> codes, LocalDate from, LocalDate to, LocalDate asOf, boolean live) {
        LocalDate today = ui.today(), since = days.firstDay();
        if (asOf.isAfter(today)) asOf = today;
        if (to.isAfter(asOf)) to = asOf;
        List<Long> ids = codes.stream().map(ActivationCode::getId).toList();
        Map<LocalDate, Map<Integer, Long>> kept = new HashMap<>();
        if (!ids.isEmpty() && !from.isAfter(to)) {
            final LocalDate f = from, t = to;
            for (Object[] r : chunked(ids, part -> days.retainedByCode(part, f, t, AGES7))) {
                kept.computeIfAbsent((LocalDate) r[1], k -> new HashMap<>()).merge(((Number) r[2]).intValue(), ((Number) r[3]).longValue(), Long::sum);
            }
        }
        List<RetRow> rows = new ArrayList<>();
        long[] sumBack = new long[8], sumSize = new long[8];
        for (LocalDate d = to; !d.isBefore(from); d = d.minusDays(1)) {
            long size = kept.getOrDefault(d, Map.of()).getOrDefault(0, 0L);
            List<RetCell> cells = new ArrayList<>();
            for (int age : DAYS7) {
                LocalDate on = d.plusDays(age);
                boolean now = on.equals(asOf);
                if (on.isAfter(asOf) || (now && !live)) {
                    cells.add(new RetCell(null, "wait"));
                } else if (!counted(since, on)) {
                    cells.add(new RetCell(null, "none"));
                } else if (size == 0) {
                    cells.add(new RetCell(null, "zero"));
                } else if (now) {
                    cells.add(new RetCell(pct(Math.min(kept.getOrDefault(d, Map.of()).getOrDefault(age, 0L), size), size), "live"));
                } else {
                    long back = Math.min(kept.getOrDefault(d, Map.of()).getOrDefault(age, 0L), size);
                    sumBack[age] += back;
                    sumSize[age] += size;
                    cells.add(new RetCell(pct(back, size), "ok"));
                }
            }
            rows.add(new RetRow(d.equals(today) ? "今天" : d.equals(today.minusDays(1)) ? "昨天" : MD.format(d) + " " + WEEK[d.getDayOfWeek().getValue() - 1], d.equals(today), cells));
        }
        List<RetCell> avg = new ArrayList<>();
        for (int age : DAYS7) {
            avg.add(sumSize[age] == 0 ? new RetCell(null, "none") : new RetCell(pct(sumBack[age], sumSize[age]), "ok"));
        }
        rows.add(0, new RetRow("合计", false, avg));
        return new Ret(rows, sumSize[1] == 0 ? null : pct(sumBack[1], sumSize[1]), sumSize[7] == 0 ? null : pct(sumBack[7], sumSize[7]), sumSize[7]);
    }

    private static final List<Integer> AGES_D7 = List.of(0, 7);

    /** 一组口令 from..to 激活的设备合计的 7 日留存，和 retention7 的 d7 一样算（asOf 同 retention7），只查第 0 天和第 7 天；没有满 7 天的激活时为空 */
    public Integer d7(List<ActivationCode> codes, LocalDate from, LocalDate to, LocalDate asOf) {
        LocalDate today = ui.today();
        LocalDate cut = asOf.isAfter(today) ? today : asOf;
        LocalDate end = to.isAfter(cut) ? cut : to;
        List<Long> ids = codes.stream().map(ActivationCode::getId).toList();
        if (ids.isEmpty() || from.isAfter(end)) return null;
        Map<Long, Long> one = new HashMap<>();
        for (Long id : ids) one.put(id, 0L);
        return d7(chunked(ids, part -> days.retainedByCode(part, from, end, AGES_D7)), one, cut).get(0L);
    }

    /** 每组口令各自的 7 日留存（groupOfCode：口令 ID → 组，比如代理 ID），一条查询算完所有代理；按真实数据，看到今天 */
    public Map<Long, Integer> d7By(Map<Long, Long> groupOfCode, LocalDate from, LocalDate to) {
        LocalDate today = ui.today();
        LocalDate end = to.isAfter(today) ? today : to;
        if (groupOfCode.isEmpty() || from.isAfter(end)) return Map.of();
        return d7(days.retainedBetween(from, end.plusDays(7), from, end, AGES_D7), groupOfCode, today);
    }

    /** rows 是 [口令 ID, 激活日, 第几天, 数]；第 7 天在 cut 那天或以后（还没过完）、那天还没统计的激活日不算 */
    private Map<Long, Integer> d7(List<Object[]> rows, Map<Long, Long> groupOfCode, LocalDate cut) {
        LocalDate since = days.firstDay();
        Map<Long, Map<LocalDate, long[]>> per = new HashMap<>();
        for (Object[] r : rows) {
            Long g = groupOfCode.get((Long) r[0]);
            if (g == null) continue;
            long[] v = per.computeIfAbsent(g, k -> new HashMap<>()).computeIfAbsent((LocalDate) r[1], k -> new long[2]);
            v[((Number) r[2]).intValue() == 0 ? 0 : 1] += ((Number) r[3]).longValue();
        }
        Map<Long, Integer> out = new HashMap<>();
        per.forEach((g, byDay) -> {
            long back = 0, size = 0;
            for (var e : byDay.entrySet()) {
                LocalDate on = e.getKey().plusDays(7);
                long n = e.getValue()[0];
                if (n == 0 || !on.isBefore(cut) || !counted(since, on)) continue;
                back += Math.min(e.getValue()[1], n);
                size += n;
            }
            Integer p = pct(back, size);
            if (p != null) out.put(g, p);
        });
        return out;
    }

    /** 每个口令的日活、新激活、留存、累计激活 */
    private Map<Long, Agg> perCode(Scope scope, Map<Long, ActivationCode> byId, LocalDate from, LocalDate today) {
        Map<Long, Agg> out = new HashMap<>();
        if (byId.isEmpty()) return out;
        boolean all = "all".equals(scope.kind());
        List<Long> ids = new ArrayList<>(byId.keySet());
        Function<Long, Agg> agg = id -> out.computeIfAbsent(id, k -> new Agg());
        for (Object[] r : all ? days.activeByCodeAll(from, today) : chunked(ids, part -> days.activeByCode(part, from, today))) {
            Long id = (Long) r[0];
            if (byId.containsKey(id)) agg.apply(id).dau.merge((LocalDate) r[1], ((Number) r[2]).longValue(), Long::sum);
        }
        LocalDate keptFrom = from.minusDays(30);
        for (Object[] r : all ? days.retainedByCodeAll(keptFrom, today, AGES) : chunked(ids, part -> days.retainedByCode(part, keptFrom, today, AGES))) {
            Long id = (Long) r[0];
            if (byId.containsKey(id)) {
                agg.apply(id).kept.computeIfAbsent((LocalDate) r[1], k -> new HashMap<>()).merge(((Number) r[2]).intValue(), ((Number) r[3]).longValue(), Long::sum);
            }
        }
        for (Object[] r : records.confirmedSince(ui.startOf(keptFrom))) {
            Long id = (Long) r[0];
            if (r[1] == null || !byId.containsKey(id)) continue;
            agg.apply(id).fresh.merge(ui.localDate((LocalDateTime) r[1]), 1L, Long::sum);
        }
        for (Object[] r : records.countConfirmedGroupByCode()) {
            Long id = (Long) r[0];
            if (byId.containsKey(id)) agg.apply(id).total += ((Number) r[1]).longValue();
        }
        return out;
    }

    private static List<Object[]> chunked(List<Long> ids, Function<List<Long>, List<Object[]>> q) {
        List<Object[]> out = new ArrayList<>();
        for (int i = 0; i < ids.size(); i += 900) out.addAll(q.apply(ids.subList(i, Math.min(ids.size(), i + 900))));
        return out;
    }

    /** 那天已经在统计了 */
    private static boolean counted(LocalDate since, LocalDate day) {
        return since != null && !day.isBefore(since);
    }

    /** 近 cohorts 个激活日（第 age 天已经过完、那天也在统计）新激活的，第 age 天还在用的：[回来的, 新激活的] */
    private static long[] retention(Agg a, LocalDate since, LocalDate today, int age, int cohorts) {
        long back = 0, size = 0;
        for (int i = 0; i < cohorts; i++) {
            LocalDate d = today.minusDays(age + 1 + i);
            if (!counted(since, d.plusDays(age))) continue;
            back += a.kept(d, age);
            size += a.fresh(d);
        }
        return new long[]{Math.min(back, size), size};
    }

    private static Integer pct(long n, long of) {
        return of == 0 ? null : com.ota.platform.common.Percent.of(n, of);
    }

    private List<Bar> bars(Agg a, LocalDate since, LocalDate from, LocalDate today, int range) {
        long max = 1;
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) max = Math.max(max, Math.max(a.dau(d), a.fresh(d)));
        int step = range <= 14 ? 1 : range <= 31 ? 5 : range <= 62 ? 7 : 15;
        List<Bar> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(today); d = d.plusDays(1)) {
            boolean none = !counted(since, d);
            long dau = a.dau(d), fresh = Math.min(a.fresh(d), dau);
            String day = (d.equals(today) ? "今天" : MD.format(d)) + " " + WEEK[d.getDayOfWeek().getValue() - 1];
            String tip;
            if (none) {
                tip = day + "：那时还没开始统计日活";
            } else {
                AdminController.Delta vs = counted(since, d.minusDays(1)) ? AdminController.delta(dau, a.dau(d.minusDays(1))) : null;
                tip = day + "：日活 " + dau + "（新激活 " + fresh + "，之前激活的 " + (dau - fresh) + "）"
                        + (vs == null ? "" : "，比前一天" + (vs.dir().equals("up") ? "多 " : vs.dir().equals("down") ? "少 " : " ") + vs.text())
                        + (d.equals(today) ? "，今天还没过完" : "");
            }
            boolean labeled = ChronoUnit.DAYS.between(d, today) % step == 0;
            out.add(new Bar(labeled ? (d.equals(today) ? "今天" : MD.format(d)) : "", tip, dau, fresh,
                    dau == 0 ? 2 : (int) Math.max(3, Math.round(dau * (double) PLOT_PX / max)),
                    fresh == 0 ? 0 : (int) Math.max(2, Math.round(fresh * (double) PLOT_PX / max)), d.equals(today), none));
        }
        return out;
    }

    /** 最近 count 天每天新激活的设备，之后第 1 / 3 / 7 / 14 / 30 天还在用的；第一行是这些天加起来的 */
    private List<Cohort> cohorts(Agg a, LocalDate since, LocalDate today, int count) {
        List<Cohort> rows = new ArrayList<>();
        long[] sumBack = new long[AGES.size()], sumSize = new long[AGES.size()];
        long sizeAll = 0;
        for (int i = 0; i < count; i++) {
            LocalDate d = today.minusDays(i);
            long size = a.fresh(d);
            List<Cell> cells = new ArrayList<>();
            for (int k = 0; k < AGES.size(); k++) {
                int age = AGES.get(k);
                LocalDate on = d.plusDays(age);
                String when = MD.format(d) + " 新激活的 " + size + " 台，第 " + age + " 天（" + MD.format(on) + "）";
                if (on.isAfter(today)) {
                    cells.add(new Cell(null, 0, "wait", when + "还没到"));
                } else if (!counted(since, on)) {
                    cells.add(new Cell(null, 0, "none", when + "：那时还没开始统计日活"));
                } else if (size == 0) {
                    cells.add(new Cell(null, 0, "zero", MD.format(d) + " 没有新激活"));
                } else if (on.equals(today)) {
                    long back = Math.min(a.kept(d, age), size);
                    cells.add(new Cell(pct(back, size), back, "live", when + "是今天，到现在打开过的有 " + back + " 台，还会涨"));
                } else {
                    long back = Math.min(a.kept(d, age), size);
                    sumBack[k] += back;
                    sumSize[k] += size;
                    cells.add(new Cell(pct(back, size), back, "ok", when + "打开过的有 " + back + " 台"));
                }
            }
            sizeAll += size;
            rows.add(new Cohort(i == 0 ? "今天" : i == 1 ? "昨天" : MD.format(d) + " " + WEEK[d.getDayOfWeek().getValue() - 1], size, cells));
        }
        List<Cell> avg = new ArrayList<>();
        for (int k = 0; k < AGES.size(); k++) {
            avg.add(sumSize[k] == 0 ? new Cell(null, 0, "none", "还没有算得出来的")
                    : new Cell(pct(sumBack[k], sumSize[k]), sumBack[k], "ok", "这些天新激活、第 " + AGES.get(k) + " 天已经过完的 " + sumSize[k] + " 台里，打开过的有 " + sumBack[k] + " 台"));
        }
        rows.add(0, new Cohort("合计", sizeAll, avg));
        return rows;
    }
}
