package com.ota.platform.web.agent;

import com.ota.platform.web.AdminUi;
import com.ota.platform.web.admin.AdminController;
import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.agent.Deduction;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.codes.ActivationRecord;
import com.ota.platform.codes.ActivationRecordRepo;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 代理端页面数据。代理自己访问 /agent 和管理员预览 /admin/agents/{id}/preview 共用这里，
 * 保证管理员预览看到的和代理看到的完全一致。数是怎么扣出来的见 AgentLedger，什么时候写死见 Settlement
 */
@Component("agentPortal")
public class AgentPortal {

    private static final int PAGE_SIZE = 50;
    private static final int TREND_DAYS = 14;
    private static final int PLOT_PX = 140;
    /** 自定义时间段最长一年 */
    private static final int MAX_SPAN = 366;
    /** 时间段超过这么多天，趋势图按月画 */
    private static final int DAILY_MAX = 92;
    /** 按月统计列几个月 */
    private static final int MONTHS = 6;
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM-dd");
    private static final String[] WEEK = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final ActivationCodeRepo codeRepo;
    private final ActivationRecordRepo recordRepo;
    private final AgentRepo agentRepo;
    private final AdminUi ui;
    private final com.ota.platform.notice.NoticeService notices;
    private final AppService apps;
    private final com.ota.platform.agent.AgentService agentService;
    private final AgentLedger ledger;
    private final com.ota.platform.web.admin.ActiveReport active;

    public AgentPortal(ActivationCodeRepo codeRepo, ActivationRecordRepo recordRepo, AgentRepo agentRepo, AdminUi ui, AppService apps,
                       com.ota.platform.agent.AgentService agentService, com.ota.platform.notice.NoticeService notices,
                       AgentLedger ledger, com.ota.platform.web.admin.ActiveReport active) {
        this.notices = notices;
        this.agentService = agentService;
        this.codeRepo = codeRepo;
        this.recordRepo = recordRepo;
        this.agentRepo = agentRepo;
        this.ui = ui;
        this.apps = apps;
        this.ledger = ledger;
        this.active = active;
    }

    private static final DateTimeFormatter RET_MD = DateTimeFormatter.ofPattern("MM-dd");

    /** 留存页的时间段按钮（选哪些激活日）：结算按 7 日留存，所以给够一个月以上 */
    public static final List<RangeOpt> RET_RANGE_OPTS = List.of(new RangeOpt("7", "近 7 天"), new RangeOpt("14", "近 14 天"),
            new RangeOpt("30", "近 30 天"), new RangeOpt("month", "本月"), new RangeOpt("last", "上月"));

    /** 代理的「留存」页：选的时间段里每个激活日，新激活的设备第 1..7 天还打开的占多少（只给百分比，结算按合计的 7 日留存） */
    public String retention(Agent agent, String base, boolean preview, String rangeKey, String fromText, String toText, Model model) {
        AgentLedger.Family f = ledger.family(agent);
        List<ActivationCode> codes = f == null ? List.of() : agent.isSub() ? f.codesOf(agent) : f.codes();
        common(agent, base, preview, model);
        Range r = range("".equals(rangeKey == null ? "" : rangeKey.trim()) ? "30" : rangeKey, fromText, toText, cutoffDay(agent));
        var ret = active.retention7(codes, r.from(), r.to(), cutoffDay(agent), cutoff(agent) == null);
        model.addAttribute("retRows", ret.rows());
        model.addAttribute("retDays", com.ota.platform.web.admin.ActiveReport.DAYS7);
        model.addAttribute("retD1", ret.d1());
        model.addAttribute("retD7", ret.d7());
        model.addAttribute("retMatured", ret.matured());
        model.addAttribute("retRangeLabel", r.label());
        model.addAttribute("retRangeOpts", RET_RANGE_OPTS);
        model.addAttribute("range", r.key());
        model.addAttribute("rangeFrom", r.from().toString());
        model.addAttribute("rangeTo", r.to().toString());
        model.addAttribute("rangeMax", cutoffDay(agent).toString());
        LocalDate since = active.since();
        model.addAttribute("retStarted", since != null);
        model.addAttribute("retSince", since == null ? null : since.getYear() == ui.today().getYear() ? RET_MD.format(since) : since.toString());
        return "agent/retention";
    }

    /** 时间段截到的那天：和概览一样，实时今天、每日 0 点昨天、手动更新那天 */
    private LocalDate cutoffDay(Agent agent) {
        LocalDateTime c = cutoff(agent);
        return c == null ? ui.today() : ui.localDate(c);
    }

    public List<OtaApp> allApps() {
        return apps.all();
    }

    /** 代理后台的申请记录 */
    public record RequestRow(Long id, int count, int maxUses, String note, String status, String statusLabel,
                             Integer codeCount, String reviewNote, LocalDateTime createdAt, LocalDateTime reviewedAt,
                             boolean custom, List<ItemRow> items, int rejectedCount) {}

    /** 自定义申请里的一个口令和结果 */
    public record ItemRow(String code, String status, String reason) {}

    /** 代理端看到的口令：用了几次是 TA 看到的（扣掉的不算，见 CodeLimits），不带实际用了几次。字段名和口令上的一样 */
    public record CodeView(Long id, String code, Boolean enabled, LocalDateTime expiresAt, int maxUses, int usedCount, Long subAgentId) {
        static List<CodeView> of(List<ActivationCode> list) {
            return list.stream().map(c -> new CodeView(c.getId(), c.getCode(), c.getEnabled(), c.getExpiresAt(), c.getMaxUses(),
                    c.limitUsed(), c.getSubAgentId())).toList();
        }
    }

    public String requests(Agent agent, String base, boolean preview, Model model) {
        List<RequestRow> rows = new ArrayList<>();
        List<com.ota.platform.agent.CodeRequest> mine = agentService.ofAgent(agent.getId(), 100);
        Map<Long, List<com.ota.platform.agent.CodeRequestItem>> itemsBy = agentService.itemsOf(
                mine.stream().filter(com.ota.platform.agent.CodeRequest::isCustom).map(com.ota.platform.agent.CodeRequest::getId).toList());
        for (com.ota.platform.agent.CodeRequest r : mine) {
            List<ItemRow> its = itemsBy.getOrDefault(r.getId(), List.of()).stream().map(i -> new ItemRow(i.getCode(), i.getStatus(), i.getReason())).toList();
            int rejected = (int) its.stream().filter(i -> "REJECTED".equals(i.status())).count();
            rows.add(new RequestRow(r.getId(), r.getCount(), r.getMaxUses(), r.getNote(),
                    r.getStatus(), switch (r.getStatus()) {
                        case "PENDING" -> "待审核";
                        case "APPROVED" -> r.isAuto() ? "已通过（自动）" : "已通过";
                        case "REJECTED" -> "没通过";
                        case "CANCELLED" -> "已撤回";
                        default -> r.getStatus();
                    }, r.getCodeCount(), r.getReviewNote(), r.getCreatedAt(), r.getReviewedAt(), r.isCustom(), its, rejected));
        }
        long requestedToday = agentService.requestedSince(agent, ui.startOfToday());
        common(agent, base, preview, model);
        model.addAttribute("requests", rows);
        model.addAttribute("requestedToday", requestedToday);
        model.addAttribute("remaining", agentService.remaining(agent, ui.startOfToday()));
        model.addAttribute("maxPerRequest", com.ota.platform.agent.AgentService.MAX_PER_REQUEST);
        model.addAttribute("pendingRequests", rows.stream().filter(r -> "PENDING".equals(r.status())).count());
        return "agent/requests";
    }

    /** 有多个 App 时记录旁边要标出是哪个 App 的；只有一个时为空 */
    public Map<Long, String> appNames() {
        List<OtaApp> all = apps.all();
        if (all.size() < 2) return Map.of();
        Map<Long, String> m = new HashMap<>();
        for (OtaApp a : all) m.put(a.getId(), a.getName());
        return m;
    }

    /** 所有 App 的名字（导出记录时每行都写上） */
    public Map<Long, String> allAppNames() {
        Map<Long, String> m = new HashMap<>();
        for (OtaApp a : apps.all()) m.put(a.getId(), a.getName());
        return m;
    }

    /** 后台用：每个一级代理的口令数、真实有效激活、今日新增、代理端当前看到的数（二级代理的都算在上级名下） */
    public List<AgentRow> adminRows() {
        List<Agent> agents = agentRepo.findByParentIdIsNullOrderByCreatedAtDesc();
        if (agents.isEmpty()) return List.of();
        Map<Long, Long> subsByParent = new HashMap<>();
        for (Agent s : agentRepo.findByParentIdIsNotNull()) subsByParent.merge(s.getParentId(), 1L, Long::sum);
        Map<Long, List<ActivationCode>> codesByAgent = new HashMap<>();
        Map<Long, Long> agentOfCode = new HashMap<>();
        Map<Long, Integer> usableByAgent = new HashMap<>();
        LocalDateTime now = LocalDateTime.now();
        for (ActivationCode c : codeRepo.findAll()) {
            if (c.getAgentId() == null) continue;
            codesByAgent.computeIfAbsent(c.getAgentId(), k -> new ArrayList<>()).add(c);
            agentOfCode.put(c.getId(), c.getAgentId());
            if (c.canActivate()) usableByAgent.merge(c.getAgentId(), 1, Integer::sum);
        }
        Map<Long, Long> totalByCode = toMap(recordRepo.countConfirmedGroupByCode());
        // 近 30 天每个代理每天的有效激活：今天是最后一格
        LocalDate todayDate = ui.today();
        Map<Long, long[]> dailyByAgent = new HashMap<>();
        for (Object[] r : recordRepo.confirmedSince(ui.startOfToday().minusDays(29))) {
            Long agentId = agentOfCode.get((Long) r[0]);
            if (agentId == null || r[1] == null) continue;
            long back = ChronoUnit.DAYS.between(ui.localDate((LocalDateTime) r[1]), todayDate);
            if (back < 0 || back > 29) continue;
            dailyByAgent.computeIfAbsent(agentId, k -> new long[30])[29 - (int) back]++;
        }
        Set<Long> deducted = ledger.deducted();
        Map<Long, Integer> d7 = active.d7By(agentOfCode, todayDate.minusDays(29), todayDate);
        List<AgentRow> rows = new ArrayList<>();
        for (Agent a : agents) {
            List<ActivationCode> mine = codesByAgent.getOrDefault(a.getId(), List.of());
            List<Long> ids = mine.stream().map(ActivationCode::getId).toList();
            long total = ids.stream().mapToLong(id -> totalByCode.getOrDefault(id, 0L)).sum();
            long[] daily = dailyByAgent.getOrDefault(a.getId(), new long[30]);
            long today = daily[29];
            LocalDateTime cutoff = cutoff(a, null);
            long visible;
            if (ids.isEmpty()) visible = 0;
            else if (deducted.contains(a.getId()) || Deduction.parse(a.getDeductionTiers()).hasTiers()) visible = ledger.family(a, mine).top(cutoff).total();
            else visible = cutoff == null ? total : recordRepo.countConfirmedAsOf(ids, cutoff);
            AgentRow row = new AgentRow(a, ids.size(), total, today, cutoff, visible, Deduction.parse(a.getDeductionTiers()).describe());
            row.extra(daily, usableByAgent.getOrDefault(a.getId(), 0));
            row.subCount = subsByParent.getOrDefault(a.getId(), 0L);
            row.d7 = d7.get(a.getId());
            rows.add(row);
        }
        return rows;
    }

    // ==================== 二级代理 ====================

    /**
     * 二级代理一行。today、week、total、spark 是上级看到的（按上级的扣量和截止时间，「今日」跟上级的数据概览一致）；
     * seen 是二级代理自己看到的累计；real 是实际有效激活，只给管理员看，代理端是 -1。
     * 扣量档位和备注放在 tiers、note 里，不用代理对象上的 deductionTiers、remark：页面字段按名字发，
     * 用了那两个名字，一级代理自己的扣量和管理员写的备注也会跟着发到代理端
     */
    public record SubRow(Agent agent, int codeCount, int usable, long today, long week, long total, long seen, long real,
                         String deductionDesc, String mode, LocalDateTime cutoff, boolean active, List<Integer> spark,
                         boolean sparkEmpty, List<CodeView> codes, String tiers, String note, long inRange, long seenInRange) {}

    public List<SubRow> subRows(Agent p, boolean admin) {
        return subRows(p, admin, null);
    }

    /** r 是二级代理页上选的时间段：inRange 是上级看到的、seenInRange 是二级代理自己看到的 */
    public List<SubRow> subRows(Agent p, boolean admin, Range r) {
        List<Agent> subs = agentRepo.findByParentIdOrderByCreatedAtDesc(p.getId());
        if (subs.isEmpty()) return List.of();
        AgentLedger.Family f = ledger.family(p, null).preload(subs);
        List<ActivationCode> all = f.codes();
        Map<Long, List<ActivationCode>> bySub = new HashMap<>();
        Map<Long, Long> subOfCode = new HashMap<>();
        for (ActivationCode c : all) {
            if (c.getSubAgentId() == null) continue;
            bySub.computeIfAbsent(c.getSubAgentId(), k -> new ArrayList<>()).add(c);
            subOfCode.put(c.getId(), c.getSubAgentId());
        }
        LocalDateTime upCutoff = cutoff(p, null);
        LocalDate ref = refDay(p, upCutoff);
        int slots = 4 + TREND_DAYS;
        Map<Long, long[]> perSub = new HashMap<>();   // [ref 当天, 近 7 天, 累计, 时间段里的, 近 14 天每天…]
        for (var e : f.top(upCutoff).kept().entrySet()) {
            long back = ChronoUnit.DAYS.between(e.getKey(), ref);
            boolean in = r != null && r.has(e.getKey());
            for (ActivationRecord rec : e.getValue()) {
                Long s = subOfCode.get(rec.getCodeId());
                if (s == null) continue;
                long[] v = perSub.computeIfAbsent(s, k -> new long[slots]);
                v[2]++;
                if (back == 0) v[0]++;
                if (back >= 0 && back < 7) v[1]++;
                if (in) v[3]++;
                if (back >= 0 && back < TREND_DAYS) v[4 + TREND_DAYS - 1 - (int) back]++;
            }
        }
        Map<Long, Long> realByCode = admin ? toMap(recordRepo.countConfirmedGroupByCode()) : Map.of();
        boolean upOk = Boolean.TRUE.equals(p.getEnabled()) && p.canManageSubs();
        List<SubRow> rows = new ArrayList<>();
        for (Agent s : subs) {
            List<ActivationCode> codes = bySub.getOrDefault(s.getId(), List.of());
            long[] v = perSub.getOrDefault(s.getId(), new long[slots]);
            long max = 0;
            for (int i = 0; i < TREND_DAYS; i++) max = Math.max(max, v[4 + i]);
            List<Integer> spark = new ArrayList<>();
            for (int i = 0; i < TREND_DAYS; i++) spark.add(max == 0 ? 2 : (int) Math.max(2, Math.round(v[4 + i] * 28.0 / max)));
            long real = admin ? codes.stream().mapToLong(c -> realByCode.getOrDefault(c.getId(), 0L)).sum() : -1;
            LocalDateTime cutoff = cutoff(s, p);
            AgentLedger.View seen = f.sub(s, cutoff);
            long seenIn = 0;
            if (r != null) for (var e : seen.kept().entrySet()) if (r.has(e.getKey())) seenIn += e.getValue().size();
            rows.add(new SubRow(s, codes.size(), (int) codes.stream().filter(ActivationCode::canActivate).count(),
                    v[0], v[1], v[2], seen.total(), real,
                    Deduction.parse(s.getDeductionTiers()).describe(), mode(s, p), cutoff,
                    upOk && Boolean.TRUE.equals(s.getEnabled()), spark, max == 0, CodeView.of(codes),
                    s.getDeductionTiers() == null || s.getDeductionTiers().isBlank() ? "[]" : s.getDeductionTiers(),
                    s.getRemark() == null ? "" : s.getRemark(), v[3], seenIn));
        }
        return rows;
    }

    /** 代理后台「二级代理」页：开了权限的一级代理管理自己的二级代理；卡片上的数可以按时间段看 */
    public String subs(Agent p, String base, boolean preview, String rangeKey, String from, String to, Model model) {
        common(p, base, preview, model);
        LocalDate today = ui.today(), ref = refDay(p, cutoff(p));
        Range r = range(rangeKey, from, to, ref);
        List<SubRow> rows = subRows(p, false, r);
        List<ActivationCode> free = codesOf(p).stream().filter(c -> c.getSubAgentId() == null).toList();
        model.addAttribute("range", r.key());
        model.addAttribute("rangeOpts", RANGE_OPTS);
        model.addAttribute("rangeLabel", r.label());
        model.addAttribute("rangeFrom", r.from().toString());
        model.addAttribute("rangeTo", r.to().toString());
        model.addAttribute("rangeMax", ref.toString());
        model.addAttribute("subRange", rows.stream().mapToLong(SubRow::inRange).sum());
        model.addAttribute("subSeenRange", rows.stream().mapToLong(SubRow::seenInRange).sum());
        model.addAttribute("subs", rows);
        model.addAttribute("freeCodes", CodeView.of(free));
        model.addAttribute("freeUsable", free.stream().filter(ActivationCode::canActivate).count());
        model.addAttribute("givenCount", rows.stream().mapToInt(SubRow::codeCount).sum());
        model.addAttribute("subEnabled", rows.stream().filter(x -> Boolean.TRUE.equals(x.agent().getEnabled())).count());
        model.addAttribute("subToday", rows.stream().mapToLong(SubRow::today).sum());
        model.addAttribute("subWeek", rows.stream().mapToLong(SubRow::week).sum());
        model.addAttribute("subTotal", rows.stream().mapToLong(SubRow::total).sum());
        model.addAttribute("refLabel", ref.equals(today) ? "今日" : ref.equals(today.minusDays(1)) ? "昨日" : MD.format(ref));
        model.addAttribute("subLimit", p.getSubAgentLimit());
        model.addAttribute("manualCount", rows.stream().filter(x -> Agent.MODE_MANUAL.equals(x.agent().getDataMode())).count());
        model.addAttribute("upMode", p.getDataMode());
        model.addAttribute("modes", com.ota.platform.agent.SubAgentService.modesUnder(p));
        model.addAttribute("canView", Boolean.TRUE.equals(p.getCanViewRecords()));
        return "agent/subs";
    }

    private static Map<Long, Long> toMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) m.put((Long) r[0], (Long) r[1]);
        return m;
    }

    /** 代理能看到的数据截止时间；null 表示实时。二级代理看不到比上级更新的数据：取两者里早的那个 */
    public LocalDateTime cutoff(Agent a) {
        return cutoff(a, a.isSub() ? parentOf(a) : null);
    }

    private LocalDateTime cutoff(Agent a, Agent parent) {
        LocalDateTime own = ownCutoff(a);
        return parent == null ? own : earlier(own, ownCutoff(parent));
    }

    private LocalDateTime ownCutoff(Agent a) {
        return switch (a.getDataMode()) {
            case Agent.MODE_DAILY -> ui.startOfToday();
            case Agent.MODE_MANUAL -> a.getSnapshotAt() != null ? a.getSnapshotAt() : a.getCreatedAt();
            default -> null;
        };
    }

    /** null 是「现在」 */
    private static LocalDateTime earlier(LocalDateTime x, LocalDateTime y) {
        if (x == null) return y;
        if (y == null) return x;
        return y.isBefore(x) ? y : x;
    }

    /** 实际的数据更新方式：二级代理的截止时间由上级决定时（上级更晚更新），按上级的方式显示 */
    public String mode(Agent a) {
        return mode(a, a.isSub() ? parentOf(a) : null);
    }

    private String mode(Agent a, Agent p) {
        if (p == null) return a.getDataMode();
        LocalDateTime own = ownCutoff(a), up = ownCutoff(p);
        if (own == null && up == null) return Agent.MODE_REALTIME;
        return own == null || (up != null && up.isBefore(own)) ? p.getDataMode() : a.getDataMode();
    }

    /** 二级代理的上级；一级代理是 null */
    public Agent parentOf(Agent a) {
        return a.getParentId() == null ? null : agentRepo.findById(a.getParentId()).orElse(null);
    }

    /** 代理名下的口令：一级代理是归 TA 的全部（含分给二级代理的），二级代理是上级分给 TA 的 */
    public List<ActivationCode> codesOf(Agent a) {
        if (!a.isSub()) return codeRepo.findByAgentIdOrderByCreatedAtDesc(a.getId());
        return codeRepo.findBySubAgentIdOrderByCreatedAtDesc(a.getId()).stream()
                .filter(c -> a.getParentId().equals(c.getAgentId())).toList();
    }

    private static List<Long> idsOf(List<ActivationCode> codes) {
        return codes.stream().map(ActivationCode::getId).toList();
    }

    /** 截止时间点上这条记录是否已确认 */
    public boolean confirmedAsOf(ActivationRecord r, LocalDateTime cutoff) {
        return AgentLedger.confirmedAsOf(r, cutoff);
    }

    /** 每个代理端页面都要的：顶栏、数据时间、预览时返回哪里 */
    private void common(Agent agent, String base, boolean preview, Model model) {
        model.addAttribute("agent", agent);
        model.addAttribute("base", base);
        model.addAttribute("preview", preview);
        model.addAttribute("cutoff", cutoff(agent));
        model.addAttribute("mode", mode(agent));
        model.addAttribute("pendingRequests", agent.canRequest() ? agentService.pendingOf(agent.getId()) : 0);
        model.addAttribute("noticeUnread", notices.unread(agent));
        boolean byParent = base.startsWith("/agent/subs/");
        model.addAttribute("previewBack", byParent ? "/agent/subs" : agent.isSub() ? "/admin/agents/" + agent.getParentId() + "#subs" : "/admin/agents");
        model.addAttribute("previewBackLabel", byParent ? "返回二级代理" : agent.isSub() ? "返回上级代理" : "返回代理管理");
    }

    // ==================== 数据概览 ====================

    /** 时间段。key：7 / 14 / 30 / 90（近几天）、month（本月）、last（上月）、custom（自定义）；prev 是拿来比的上一段 */
    public record Range(String key, LocalDate from, LocalDate to, String label, LocalDate prevFrom, LocalDate prevTo, String prevLabel) {
        public int days() {
            return (int) ChronoUnit.DAYS.between(from, to) + 1;
        }

        public boolean has(LocalDate d) {
            return !d.isBefore(from) && !d.isAfter(to);
        }
    }

    /** 时间段的按钮 */
    public record RangeOpt(String key, String label) {}

    public static final List<RangeOpt> RANGE_OPTS = List.of(new RangeOpt("7", "7 天"), new RangeOpt("14", "14 天"),
            new RangeOpt("30", "30 天"), new RangeOpt("90", "90 天"), new RangeOpt("month", "本月"), new RangeOpt("last", "上月"));

    /** 趋势图的一根柱子：label 是横轴上写的（太密时空着），tip 是鼠标停上去看到的 */
    public record Bar(String label, String tip, int count, int px, boolean last) {}

    /** 按月统计的一行：partial 是还没过完的这个月（数到统计截止那天），delta 和上个月比（这个月和上月同期比） */
    public record MonthRow(String label, int count, AdminController.Delta delta, boolean partial, String note, int w) {}

    /** 时间段里每个二级代理（和自己手里没分出去的口令）带来的，按一级代理看到的算 */
    public record SubShare(String name, int count, int pct, int w, boolean own, boolean off) {}

    /** 统计到的那天（实时是今天、每日 0 点是昨天、手动是更新的那天）：时间段都截到这天 */
    public Range range(String key, String fromText, String toText, LocalDate ref) {
        String k = key == null ? "" : key.trim();
        switch (k) {
            case "7", "14", "30", "90" -> {
                int n = Integer.parseInt(k);
                LocalDate from = ref.minusDays(n - 1);
                return new Range(k, from, ref, "近 " + n + " 天", from.minusDays(n), from.minusDays(1), "前 " + n + " 天");
            }
            case "month" -> {
                LocalDate from = ref.withDayOfMonth(1), pf = from.minusMonths(1);
                LocalDate pt = pf.plusDays(Math.min(ref.getDayOfMonth(), pf.lengthOfMonth()) - 1);
                return new Range(k, from, ref, monthLabel(from, ref), pf, pt, "上月同期");
            }
            case "last" -> {
                LocalDate from = ref.withDayOfMonth(1).minusMonths(1), pf = from.minusMonths(1);
                return new Range(k, from, from.withDayOfMonth(from.lengthOfMonth()), monthLabel(from, ref),
                        pf, pf.withDayOfMonth(pf.lengthOfMonth()), monthLabel(pf, ref));
            }
            case "custom" -> {
                LocalDate from = date(fromText), to = date(toText);
                if (from != null || to != null) {
                    if (to == null || to.isAfter(ref)) to = ref;
                    if (from == null) from = to.minusDays(29);
                    if (from.isAfter(ref)) from = ref;
                    if (from.isAfter(to)) {
                        LocalDate x = from;
                        from = to;
                        to = x;
                    }
                    if (ChronoUnit.DAYS.between(from, to) >= MAX_SPAN) from = to.minusDays(MAX_SPAN - 1);
                    int n = (int) ChronoUnit.DAYS.between(from, to) + 1;
                    String label = from.equals(to) ? dayLabel(from, ref) : dayLabel(from, ref) + " 至 " + dayLabel(to, ref);
                    return new Range(k, from, to, label, from.minusDays(n), from.minusDays(1), "前 " + n + " 天");
                }
            }
            default -> {}
        }
        return range("14", null, null, ref);
    }

    private static LocalDate date(String s) {
        try {
            return s == null || s.isBlank() ? null : LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private static String monthLabel(LocalDate d, LocalDate ref) {
        return (d.getYear() == ref.getYear() ? "" : d.getYear() + " 年 ") + d.getMonthValue() + " 月";
    }

    private static String dayLabel(LocalDate d, LocalDate ref) {
        return d.getYear() == ref.getYear() ? MD.format(d) : d.toString();
    }

    public String overview(Agent agent, String base, boolean preview, String rangeKey, String from, String to, Model model) {
        LocalDateTime cutoff = cutoff(agent);
        AgentLedger.Family f = ledger.family(agent);
        List<ActivationCode> codes = f == null ? List.of() : agent.isSub() ? f.codesOf(agent) : f.codes();
        AgentLedger.View view = f == null ? AgentLedger.View.EMPTY : f.view(agent, cutoff);

        LocalDate today = ui.today();
        LocalDate ref = refDay(agent, cutoff);
        Range r = range(rangeKey, from, to, ref);

        Map<LocalDate, Integer> byDay = new HashMap<>();       // 扣量后每天的数
        Map<Long, Integer> totalByCode = new HashMap<>(), refByCode = new HashMap<>(), rangeByCode = new HashMap<>();
        Map<Long, Integer> rangeBySub = new HashMap<>();
        int total = 0;
        for (var entry : view.kept().entrySet()) {
            LocalDate day = entry.getKey();
            byDay.put(day, entry.getValue().size());
            total += entry.getValue().size();
            boolean isRef = day.equals(ref), inRange = r.has(day);
            for (ActivationRecord rec : entry.getValue()) {
                totalByCode.merge(rec.getCodeId(), 1, Integer::sum);
                if (isRef) refByCode.merge(rec.getCodeId(), 1, Integer::sum);
                if (inRange) rangeByCode.merge(rec.getCodeId(), 1, Integer::sum);
            }
        }

        int refCount = byDay.getOrDefault(ref, 0);
        // 实时和手动更新的「当天」还没过完：和前一天比到同一时刻；每日 0 点的两天都是整天
        boolean daily = Agent.MODE_DAILY.equals(mode(agent));
        int refBefore = byDay.getOrDefault(ref.minusDays(1), 0);
        if (!daily) {
            LocalDateTime same = (cutoff == null ? LocalDateTime.now() : cutoff).minusDays(1);
            refBefore = (int) view.kept().getOrDefault(ref.minusDays(1), List.of()).stream()
                    .filter(rec -> AgentLedger.time(rec) != null && !AgentLedger.time(rec).isAfter(same)).count();
        }
        int week = sum(byDay, ref.minusDays(6), ref);
        LocalDate monthStart = ref.withDayOfMonth(1), lastMonth = monthStart.minusMonths(1);
        int month = sum(byDay, monthStart, ref);
        int monthBefore = sum(byDay, lastMonth, lastMonth.plusDays(Math.min(ref.getDayOfMonth(), lastMonth.lengthOfMonth()) - 1));

        int rangeCount = sum(byDay, r.from(), r.to()), peak = 0, active = 0;
        LocalDate peakDay = null;
        for (LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) {
            int c = byDay.getOrDefault(d, 0);
            if (c > 0) active++;
            if (c > peak) {
                peak = c;
                peakDay = d;
            }
        }
        List<Bar> trend = bars(byDay, r, ref);

        common(agent, base, preview, model);
        model.addAttribute("noticeBanner", notices.banner(agent));
        model.addAttribute("latestNotices", notices.forAgent(agent).stream().limit(4).toList());
        model.addAttribute("subNames", subNames(agent));
        model.addAttribute("total", total);
        model.addAttribute("refLabel", ref.equals(today) ? "今日" : ref.equals(today.minusDays(1)) ? "昨日" : MD.format(ref));
        model.addAttribute("refCount", refCount);
        model.addAttribute("refDelta", AdminController.delta(refCount, refBefore));
        model.addAttribute("refVsLabel", daily ? "比前一天" : cutoff == null ? "比昨天同一时刻" : "比前一天同一时刻");
        model.addAttribute("weekCount", week);
        model.addAttribute("weekDelta", AdminController.delta(week, sum(byDay, ref.minusDays(13), ref.minusDays(7))));
        model.addAttribute("monthLabel", ref.getMonthValue() + " 月");
        model.addAttribute("monthCount", month);
        model.addAttribute("monthDelta", AdminController.delta(month, monthBefore));
        model.addAttribute("trend", trend);
        model.addAttribute("trendClass", r.days() > DAILY_MAX ? "by-month" : r.days() > 31 ? "range-90" : r.days() > 14 ? "range-30" : "");
        model.addAttribute("range", r.key());
        model.addAttribute("rangeOpts", RANGE_OPTS);
        model.addAttribute("rangeLabel", r.label());
        model.addAttribute("rangeFrom", r.from().toString());
        model.addAttribute("rangeTo", r.to().toString());
        model.addAttribute("rangeMax", ref.toString());
        model.addAttribute("rangeCount", rangeCount);
        model.addAttribute("rangePrevLabel", r.prevLabel());
        model.addAttribute("rangeDelta", AdminController.delta(rangeCount, sum(byDay, r.prevFrom(), r.prevTo())));
        model.addAttribute("rangeDays", r.days());
        model.addAttribute("rangeAvg", String.format(Locale.ROOT, "%.1f", rangeCount / (double) r.days()));
        model.addAttribute("rangePeak", peak);
        model.addAttribute("rangePeakDay", peakDay == null ? "" : dayLabel(peakDay, ref));
        model.addAttribute("rangeActive", active);
        model.addAttribute("months", months(byDay, ref, agent));
        Range ret = range("30", null, null, cutoffDay(agent));
        model.addAttribute("retD7", this.active.d7(codes, ret.from(), ret.to(), cutoffDay(agent)));
        model.addAttribute("retStarted", this.active.since() != null);
        model.addAttribute("subShares", agent.canManageSubs() ? subShares(agent, view, r) : List.of());
        model.addAttribute("codes", CodeView.of(codes));
        model.addAttribute("totalByCode", totalByCode);
        model.addAttribute("refByCode", refByCode);
        model.addAttribute("rangeByCode", rangeByCode);
        model.addAttribute("usableCount", codes.stream().filter(ActivationCode::canActivate).count());
        return "agent/overview";
    }

    /** 趋势图：92 天以内按天，再长按月 */
    private List<Bar> bars(Map<LocalDate, Integer> byDay, Range r, LocalDate ref) {
        List<Bar> out = new ArrayList<>();
        int n = r.days();
        if (n <= DAILY_MAX) {
            int step = n <= 14 ? 1 : n <= 31 ? 5 : n <= 62 ? 7 : 15, max = 1;
            for (LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) max = Math.max(max, byDay.getOrDefault(d, 0));
            for (LocalDate d = r.from(); !d.isAfter(r.to()); d = d.plusDays(1)) {
                int c = byDay.getOrDefault(d, 0);
                boolean labeled = ChronoUnit.DAYS.between(d, r.to()) % step == 0;
                out.add(new Bar(labeled ? MD.format(d) : "", dayLabel(d, ref) + " " + WEEK[d.getDayOfWeek().getValue() - 1] + "：" + c + " 台",
                        c, px(c, max), d.equals(ref)));
            }
            return out;
        }
        List<int[]> counts = new ArrayList<>();
        List<LocalDate> starts = new ArrayList<>();
        int max = 1;
        for (LocalDate m = r.from().withDayOfMonth(1); !m.isAfter(r.to()); m = m.plusMonths(1)) {
            LocalDate a = m.isBefore(r.from()) ? r.from() : m, b = m.withDayOfMonth(m.lengthOfMonth());
            int c = sum(byDay, a, b.isAfter(r.to()) ? r.to() : b);
            counts.add(new int[]{c});
            starts.add(m);
            max = Math.max(max, c);
        }
        for (int i = 0; i < starts.size(); i++) {
            LocalDate m = starts.get(i);
            int c = counts.get(i)[0];
            String label = m.getMonthValue() == 1 || i == 0 ? m.getYear() % 100 + "/" + m.getMonthValue() : m.getMonthValue() + " 月";
            out.add(new Bar(label, monthLabel(m, ref) + "：" + c + " 台", c, px(c, max), i == starts.size() - 1 && !r.to().isBefore(ref)));
        }
        return out;
    }

    private static int px(int c, int max) {
        return c == 0 ? 2 : Math.max(4, c * PLOT_PX / max);
    }

    /** 最近几个月每个月的（从代理开通的那个月起） */
    private List<MonthRow> months(Map<LocalDate, Integer> byDay, LocalDate ref, Agent agent) {
        LocalDate first = ref.withDayOfMonth(1);
        LocalDate born = agent.getCreatedAt() == null ? first : ui.localDate(agent.getCreatedAt()).withDayOfMonth(1);
        List<LocalDate> list = new ArrayList<>();
        for (int i = 0; i < MONTHS; i++) {
            LocalDate m = first.minusMonths(i);
            if (i > 0 && m.isBefore(born) && sum(byDay, m, m.withDayOfMonth(m.lengthOfMonth())) == 0) break;
            list.add(m);
        }
        int max = 1;
        int[] counts = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            LocalDate m = list.get(i);
            counts[i] = sum(byDay, m, i == 0 ? ref : m.withDayOfMonth(m.lengthOfMonth()));
            max = Math.max(max, counts[i]);
        }
        List<MonthRow> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            LocalDate m = list.get(i), pm = m.minusMonths(1);
            boolean partial = i == 0 && ref.getDayOfMonth() < m.lengthOfMonth();
            int before = partial ? sum(byDay, pm, pm.plusDays(Math.min(ref.getDayOfMonth(), pm.lengthOfMonth()) - 1))
                    : sum(byDay, pm, pm.withDayOfMonth(pm.lengthOfMonth()));
            out.add(new MonthRow(monthLabel(m, ref), counts[i], AdminController.delta(counts[i], before), partial,
                    partial ? "截至 " + MD.format(ref) : "", counts[i] == 0 ? 0 : Math.max(3, counts[i] * 100 / max)));
        }
        return out;
    }

    /** 一级代理看到的时间段里的数，按二级代理分开；自己手里的口令算一行 */
    private List<SubShare> subShares(Agent p, AgentLedger.View view, Range r) {
        List<Agent> subs = agentRepo.findByParentIdOrderByCreatedAtDesc(p.getId());
        if (subs.isEmpty()) return List.of();
        Map<Long, Long> subOfCode = new HashMap<>();
        for (ActivationCode c : codeRepo.findByAgentIdOrderByCreatedAtDesc(p.getId())) if (c.getSubAgentId() != null) subOfCode.put(c.getId(), c.getSubAgentId());
        Map<Long, Integer> by = new HashMap<>();
        int all = 0;
        for (var e : view.kept().entrySet()) {
            if (!r.has(e.getKey())) continue;
            for (ActivationRecord rec : e.getValue()) {
                by.merge(subOfCode.getOrDefault(rec.getCodeId(), 0L), 1, Integer::sum);
                all++;
            }
        }
        List<SubShare> rows = new ArrayList<>();
        int max = 1;
        for (int n : by.values()) max = Math.max(max, n);
        for (Agent s : subs) {
            int n = by.getOrDefault(s.getId(), 0);
            rows.add(new SubShare(s.getName(), n, com.ota.platform.common.Percent.of(n, all), n == 0 ? 0 : Math.max(3, n * 100 / max), false,
                    !Boolean.TRUE.equals(s.getEnabled())));
        }
        rows.sort(Comparator.comparingInt(SubShare::count).reversed());
        int own = by.getOrDefault(0L, 0);
        rows.add(new SubShare("自己手里的口令", own, com.ota.platform.common.Percent.of(own, all), own == 0 ? 0 : Math.max(3, own * 100 / max), true, false));
        return rows;
    }

    /** 口令分给了哪个二级代理：二级代理 ID → 名字（没开二级代理时为空） */
    private Map<Long, String> subNames(Agent agent) {
        if (agent.isSub()) return Map.of();
        Map<Long, String> m = new HashMap<>();
        for (Agent s : agentRepo.findByParentIdOrderByCreatedAtDesc(agent.getId())) m.put(s.getId(), s.getName());
        return m;
    }

    /** 口令 ID → 分给的二级代理的名字 */
    private Map<Long, String> subOfCode(List<ActivationCode> codes, Map<Long, String> subNames) {
        Map<Long, String> m = new HashMap<>();
        if (subNames.isEmpty()) return m;
        for (ActivationCode c : codes) if (c.getSubAgentId() != null && subNames.containsKey(c.getSubAgentId())) m.put(c.getId(), subNames.get(c.getSubAgentId()));
        return m;
    }

    // ==================== 激活记录 ====================

    public String records(Agent agent, String base, boolean preview,
                          String status, String code, int page, Model model) {
        LocalDateTime cutoff = cutoff(agent);
        AgentLedger.Family f = ledger.family(agent);
        List<ActivationCode> codes = f == null ? List.of() : agent.isSub() ? f.codesOf(agent) : f.codes();
        String codeFilter = code.trim();
        PageRequest pr = PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "activatedAt"));
        Specification<ActivationRecord> spec = visibleRecords(f, agent, cutoff, codes, status, codeFilter);
        Page<ActivationRecord> records = spec == null ? Page.empty(pr) : recordRepo.findAll(spec, pr);

        common(agent, base, preview, model);
        model.addAttribute("records", records);
        model.addAttribute("status", status);
        model.addAttribute("code", codeFilter);
        model.addAttribute("codes", CodeView.of(codes));
        model.addAttribute("filtered", !"all".equals(status) || !codeFilter.isEmpty());
        model.addAttribute("appNames", appNames());
        model.addAttribute("subOfCode", subOfCode(codes, subNames(agent)));
        return "agent/records";
    }

    /** 导出代理能看到的激活记录（和记录列表同样的筛选、截止时间、扣量），最多 EXPORT_MAX 条，新的在前 */
    public List<ActivationRecord> exportRecords(Agent agent, String status, String code) {
        AgentLedger.Family f = ledger.family(agent);
        List<ActivationCode> codes = f == null ? List.of() : agent.isSub() ? f.codesOf(agent) : f.codes();
        Specification<ActivationRecord> spec = visibleRecords(f, agent, cutoff(agent), codes, status, code.trim());
        if (spec == null) return List.of();
        return recordRepo.findAll(spec, PageRequest.of(0, EXPORT_MAX, Sort.by(Sort.Direction.DESC, "activatedAt"))).getContent();
    }

    public static final int EXPORT_MAX = 50_000;

    /** 代理能看到的激活记录：自己的口令、截止时间以前、扣掉的不算；没有口令时返回 null */
    private Specification<ActivationRecord> visibleRecords(AgentLedger.Family f, Agent agent, LocalDateTime cutoff, List<ActivationCode> codes,
                                                           String status, String codeFilter) {
        List<Long> ids = idsOf(codes);
        if (ids.isEmpty() || f == null) return null;
        Set<Long> deducted = f.hidden(agent, cutoff);
        return (root, cq, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(root.get("codeId").in(ids));
            if (!deducted.isEmpty()) where.add(cb.not(root.get("id").in(deducted)));
            if (cutoff != null) where.add(cb.lessThanOrEqualTo(root.get("activatedAt"), cutoff));
            if ("confirmed".equals(status)) {
                where.add(cb.isTrue(root.get("confirmed")));
                if (cutoff != null) {
                    where.add(cb.or(cb.isNull(root.get("confirmedAt")), cb.lessThanOrEqualTo(root.get("confirmedAt"), cutoff)));
                }
            } else if ("pending".equals(status)) {
                List<Predicate> pending = new ArrayList<>(List.of(cb.isNull(root.get("confirmed")), cb.isFalse(root.get("confirmed"))));
                if (cutoff != null) pending.add(cb.greaterThan(root.get("confirmedAt"), cutoff));
                where.add(cb.or(pending.toArray(new Predicate[0])));
                where.add(cb.isNull(root.get("fastMs")));
                where.add(cb.isNull(root.get("dupOf")));
            } else if ("fast".equals(status)) {
                where.add(cb.isNotNull(root.get("fastMs")));
            } else if ("dup".equals(status)) {
                where.add(cb.isNotNull(root.get("dupOf")));
            }
            if (!codeFilter.isEmpty()) where.add(cb.equal(root.get("codeText"), codeFilter));
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    // ==================== 公告、账号 ====================

    /** 代理的「公告」页：列出能看到的公告，代理自己打开时记为看过（预览不记）；开了二级代理的还有 TA 发给二级代理的 */
    public String notices(Agent agent, String base, boolean preview, Model model) {
        List<com.ota.platform.notice.NoticeService.AgentView> list = notices.forAgent(agent);
        common(agent, base, preview, model);
        model.addAttribute("notices", list);
        if (!preview) notices.markRead(agent, list.stream().map(com.ota.platform.notice.NoticeService.AgentView::id).toList());
        model.addAttribute("noticeUnread", preview ? list.stream().filter(v -> !v.read()).count() : 0);
        boolean send = agent.canManageSubs();
        model.addAttribute("canSend", send);
        model.addAttribute("sent", send ? notices.ownList(agent) : List.of());
        model.addAttribute("subCount", send ? agentRepo.countByParentId(agent.getId()) : 0);
        return "agent/notices";
    }

    /** 代理的「我的账号」页 */
    public String account(Agent agent, String base, boolean preview, Model model) {
        common(agent, base, preview, model);
        model.addAttribute("codeCount", codesOf(agent).size());
        Agent p = parentOf(agent);
        model.addAttribute("parentName", p == null ? null : p.getName());
        model.addAttribute("subCount", agent.canManageSubs() ? agentRepo.countByParentId(agent.getId()) : 0);
        return "agent/account";
    }

    /** 代理端统计的「当天」：每日 0 点模式是昨天，手动模式是快照那天 */
    private LocalDate refDay(Agent agent, LocalDateTime cutoff) {
        LocalDate today = ui.today();
        return switch (mode(agent)) {
            case Agent.MODE_DAILY -> today.minusDays(1);
            case Agent.MODE_MANUAL -> ui.localDate(cutoff);
            default -> today;
        };
    }

    private int sum(Map<LocalDate, Integer> byDay, LocalDate from, LocalDate to) {
        int n = 0;
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) n += byDay.getOrDefault(d, 0);
        return n;
    }

    // ==================== 后台管理用 ====================

    public static class AgentRow {
        private final Agent agent;
        private final int codeCount;
        private final long total;
        private final long today;
        private final LocalDateTime cutoff;
        private final long visible;
        private final String deductionDesc;

        AgentRow(Agent agent, int codeCount, long total, long today, LocalDateTime cutoff, long visible, String deductionDesc) {
            this.agent = agent;
            this.codeCount = codeCount;
            this.total = total;
            this.today = today;
            this.cutoff = cutoff;
            this.visible = visible;
            this.deductionDesc = deductionDesc;
        }

        public Agent getAgent() { return agent; }
        public int getCodeCount() { return codeCount; }
        public long getTotal() { return total; }
        public long getToday() { return today; }
        public LocalDateTime getCutoff() { return cutoff; }
        public long getVisible() { return visible; }
        public String getDeductionDesc() { return deductionDesc; }

        private long yesterday, week, prevWeek, month, subCount;
        private int usable;
        private Integer d7;
        private List<Integer> spark = List.of();

        /** 开了几个二级代理 */
        public long getSubCount() { return subCount; }

        /** 近 30 天新激活的设备合计的 7 日留存（按真实设备，不扣量）；还没有满 7 天的激活时为空 */
        public Integer getD7() { return d7; }

        /** daily：近 30 天每天的有效激活，最后一格是今天 */
        void extra(long[] daily, int usable) {
            this.usable = usable;
            yesterday = daily[28];
            for (int i = 0; i < 30; i++) {
                month += daily[i];
                if (i >= 23) week += daily[i];
                else if (i >= 16) prevWeek += daily[i];
            }
            long max = 0;
            for (int i = 16; i < 30; i++) max = Math.max(max, daily[i]);
            List<Integer> px = new ArrayList<>();
            for (int i = 16; i < 30; i++) px.add(max == 0 ? 2 : (int) Math.max(2, Math.round(daily[i] * 28.0 / max)));
            spark = px;
        }

        public long getYesterday() { return yesterday; }
        public long getWeek() { return week; }
        public long getPrevWeek() { return prevWeek; }
        public long getMonth() { return month; }
        public int getUsable() { return usable; }
        /** 近 14 天每天的柱子高度（最高 28px） */
        public List<Integer> getSpark() { return spark; }
        /** 近 14 天一个有效激活都没有：卡片上写一句话，不画一排空柱子 */
        public boolean isSparkEmpty() { return spark == null || spark.stream().allMatch(px -> px == null || px <= 2); }
        /** 近 7 天比前 7 天：up / down / flat / new，都是 0 时为空 */
        public String getTrend() {
            if (week == 0 && prevWeek == 0) return "";
            if (prevWeek == 0) return "new";
            return week > prevWeek ? "up" : week < prevWeek ? "down" : "flat";
        }
        public String getTrendText() {
            if (week == 0 && prevWeek == 0) return "";
            if (prevWeek == 0) return "新增";
            long p = Math.round((week - prevWeek) * 100.0 / prevWeek);
            return p == 0 ? "持平" : Math.abs(p) + "%";
        }
    }
}
