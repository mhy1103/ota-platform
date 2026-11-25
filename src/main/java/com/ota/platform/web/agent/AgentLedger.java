package com.ota.platform.web.agent;

import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.agent.AgentSettlement;
import com.ota.platform.agent.AgentSettlementRepo;
import com.ota.platform.agent.Deduction;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.codes.ActivationRecord;
import com.ota.platform.codes.ActivationRecordRepo;
import com.ota.platform.web.AdminUi;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

/**
 * 代理看到的有效激活（扣量）。结算过的（见 Settlement）直接按结算表：哪条扣了就一直扣着，数不再变。
 * 还没结算的每天单独算：当天截止时间内的有效激活按确认时间排好，总数落在哪个档位就按那档的比例扣，扣的条数减去这天已经结算扣掉的，
 * 剩下的先按各份的激活数按比例分给各个二级代理（一级代理自己手里的口令算一份），每份里再按比例分给各个口令，
 * 每个口令扣还没结算的里面最晚确认的那几条；除不尽的零头给差得多的，一样多时给最后一条激活更晚的。
 * 当天改过档位的，改之前的已经按原来的档位结算了，之后确认的按新档位、按当天的总数单独扣。
 * 二级代理从上级看到的里面取 TA 的口令，再按 TA 自己的档位扣一遍
 */
@Component
public class AgentLedger {

    private static final Comparator<ActivationRecord> ORDER = Comparator
            .comparing(AgentLedger::time, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder()))
            .thenComparing(ActivationRecord::getId, Comparator.nullsFirst(Comparator.<Long>naturalOrder()));
    private static final List<Function<ActivationRecord, Long>> BY_CODE = List.of(ActivationRecord::getCodeId);

    private final ActivationRecordRepo recordRepo;
    private final ActivationCodeRepo codeRepo;
    private final AgentRepo agentRepo;
    private final AgentSettlementRepo settlements;
    private final AdminUi ui;

    public AgentLedger(ActivationRecordRepo recordRepo, ActivationCodeRepo codeRepo, AgentRepo agentRepo,
                       AgentSettlementRepo settlements, AdminUi ui) {
        this.recordRepo = recordRepo;
        this.codeRepo = codeRepo;
        this.agentRepo = agentRepo;
        this.settlements = settlements;
        this.ui = ui;
    }

    /** 截止时间点上这条记录是否已确认（cutoff 为 null 是现在） */
    public static boolean confirmedAsOf(ActivationRecord r, LocalDateTime cutoff) {
        if (!Boolean.TRUE.equals(r.getConfirmed())) return false;
        return cutoff == null || r.getConfirmedAt() == null || !r.getConfirmedAt().isAfter(cutoff);
    }

    /** 激活算在什么时候：确认的时间，没有确认时间的老记录用提交时间 */
    static LocalDateTime time(ActivationRecord r) {
        return r.getConfirmedAt() != null ? r.getConfirmedAt() : r.getActivatedAt();
    }

    /** 激活算在哪天 */
    LocalDate dayOf(ActivationRecord r) {
        LocalDateTime t = time(r);
        return t == null ? ui.today() : ui.localDate(t);
    }

    /**
     * 扣量以后留下的：按天，每天按确认时间从早到晚；real 是截止时间内真实的有效激活数；
     * fresh 是还没结算的记录这次是怎么算的，结算时原样写进结算表
     */
    public record View(Map<LocalDate, List<ActivationRecord>> kept, int real, Map<LocalDate, Fresh> fresh) {
        static final View EMPTY = new View(new TreeMap<>(), 0, Map.of());

        public int total() {
            int n = 0;
            for (List<ActivationRecord> l : kept.values()) n += l.size();
            return n;
        }

        public int on(LocalDate day) {
            List<ActivationRecord> l = kept.get(day);
            return l == null ? 0 : l.size();
        }

        public Set<Long> ids() {
            Set<Long> s = new HashSet<>();
            for (List<ActivationRecord> l : kept.values()) for (ActivationRecord r : l) s.add(r.getId());
            return s;
        }
    }

    /** 一天里还没结算的记录和其中这次算下来扣掉的 */
    public record Fresh(List<Long> ids, Set<Long> hidden) {}

    /** 一个代理结算过的：记录 ID → 扣没扣；哪天从什么时候起按新档位单独算；结算到什么时候（null 是从没结算过） */
    public static final class Frozen {
        static final Frozen NONE = new Frozen(Map.of(), Map.of(), null);

        private final Map<Long, Boolean> settled;
        private final Map<LocalDate, LocalDateTime> segFrom;
        private final LocalDateTime until;
        private final boolean deducts;

        Frozen(Map<Long, Boolean> settled, Map<LocalDate, LocalDateTime> segFrom, LocalDateTime until) {
            this.settled = settled;
            this.segFrom = segFrom;
            this.until = until;
            this.deducts = settled.containsValue(Boolean.TRUE);
        }

        static Frozen of(List<AgentSettlement> rows) {
            if (rows == null || rows.isEmpty()) return NONE;
            Map<Long, Boolean> settled = new HashMap<>();
            Map<LocalDate, LocalDateTime> seg = new HashMap<>();
            LocalDateTime until = null;
            for (AgentSettlement s : rows) {
                Set<Long> hidden = s.hiddenIds();
                for (Long id : s.recordIds()) settled.put(id, hidden.contains(id));
                for (Long id : hidden) settled.put(id, Boolean.TRUE);
                if (s.getSegFrom() != null) seg.merge(s.getDay(), s.getSegFrom(), (x, y) -> x.isAfter(y) ? x : y);
                if (until == null || s.getSettledTo().isAfter(until)) until = s.getSettledTo();
            }
            return new Frozen(settled, seg, until);
        }

        /** null：还没结算；true：结算时扣掉了 */
        Boolean status(Long recordId) {
            return settled.get(recordId);
        }

        public LocalDateTime until() {
            return until;
        }

        /** 结算过的里面有没有扣掉的 */
        public boolean deducts() {
            return deducts;
        }
    }

    /** 这些代理结算过的，一次读出来 */
    public Map<Long, Frozen> frozen(Collection<Long> agentIds) {
        Map<Long, List<AgentSettlement>> by = new HashMap<>();
        List<Long> ids = agentIds.stream().filter(Objects::nonNull).distinct().toList();
        if (!ids.isEmpty()) for (AgentSettlement s : settlements.findByAgentIdIn(ids)) by.computeIfAbsent(s.getAgentId(), k -> new ArrayList<>()).add(s);
        Map<Long, Frozen> out = new HashMap<>();
        for (Long id : ids) out.put(id, Frozen.of(by.get(id)));
        return out;
    }

    /** 结算时扣过量的代理（列表页上不扣量的直接数数，不用一条条算） */
    public Set<Long> deducted() {
        return new HashSet<>(settlements.deductedAgents());
    }

    /** 这个代理所在的一家：一级代理是 TA 自己，二级代理是 TA 的上级；上级不在了返回 null */
    public Family family(Agent a) {
        Agent top = a.isSub() ? agentRepo.findById(a.getParentId()).orElse(null) : a;
        return top == null ? null : new Family(top, null);
    }

    /** 已经有一级代理的口令时直接用，不再查 */
    public Family family(Agent top, List<ActivationCode> codes) {
        return new Family(top, codes);
    }

    /** 一级代理和 TA 的二级代理一起算：口令、激活记录、结算表只读一次，上级看到的按截止时间记下来 */
    public final class Family {
        private final Agent top;
        private List<ActivationCode> codes;
        private List<ActivationRecord> records;
        private Map<Long, Long> subOfCode;
        private final Map<Long, Frozen> frozen = new HashMap<>();
        private final Map<Long, Deduction> tiers = new HashMap<>();
        private final Map<LocalDateTime, View> tops = new HashMap<>();

        private Family(Agent top, List<ActivationCode> codes) {
            this.top = top;
            this.codes = codes;
        }

        public Agent top() {
            return top;
        }

        /** 归一级代理的全部口令（含分给二级代理的） */
        public List<ActivationCode> codes() {
            if (codes == null) codes = codeRepo.findByAgentIdOrderByCreatedAtDesc(top.getId());
            return codes;
        }

        /** 分给这个二级代理的 */
        public List<ActivationCode> codesOf(Agent sub) {
            return codes().stream().filter(c -> sub.getId().equals(c.getSubAgentId())).toList();
        }

        public List<ActivationRecord> records() {
            if (records == null) {
                List<Long> ids = codes().stream().map(ActivationCode::getId).toList();
                records = ids.isEmpty() ? List.of() : recordRepo.findByCodeIdIn(ids);
            }
            return records;
        }

        /** 已经读好的结算 */
        public Family known(Map<Long, Frozen> f) {
            frozen.putAll(f);
            return this;
        }

        /** 一起读这些代理（和一级代理）结算过的，免得一个个查 */
        public Family preload(Collection<Agent> agents) {
            List<Long> need = new ArrayList<>();
            if (!frozen.containsKey(top.getId())) need.add(top.getId());
            for (Agent a : agents) if (!frozen.containsKey(a.getId())) need.add(a.getId());
            if (!need.isEmpty()) frozen.putAll(AgentLedger.this.frozen(need));
            return this;
        }

        /** 刚结算完这个代理：重新读 TA 的结算 */
        void reload(Agent a) {
            frozen.remove(a.getId());
            tops.clear();
        }

        /** 结算时用：这个代理按这套档位算（改档位之前的） */
        Family tiers(Long agentId, Deduction d) {
            tiers.put(agentId, d);
            tops.clear();
            return this;
        }

        public Frozen frozen(Agent a) {
            Frozen f = frozen.get(a.getId());
            if (f == null) {
                f = AgentLedger.this.frozen(List.of(a.getId())).get(a.getId());
                frozen.put(a.getId(), f);
            }
            return f;
        }

        Deduction tiersOf(Agent a) {
            Deduction d = tiers.get(a.getId());
            return d != null ? d : Deduction.parse(a.getDeductionTiers());
        }

        /** 现在扣量、或者结算时扣过（都没有就不用一条条记录去算） */
        public boolean deducts(Agent a) {
            return tiersOf(a).hasTiers() || frozen(a).deducts();
        }

        /** 一级代理看到的（截止到 cutoff） */
        public View top(LocalDateTime cutoff) {
            View v = tops.get(cutoff);
            if (v == null) {
                Map<Long, Long> subs = subOfCode();
                List<Function<ActivationRecord, Long>> levels = List.of(r -> subs.getOrDefault(r.getCodeId(), 0L), ActivationRecord::getCodeId);
                v = credit(records(), cutoff, tiersOf(top), frozen(top), levels);
                tops.put(cutoff, v);
            }
            return v;
        }

        /** 二级代理看到的：上级在同一个截止时间看到的里面 TA 的口令，再按 TA 的档位扣 */
        public View sub(Agent s, LocalDateTime cutoff) {
            Set<Long> mine = new HashSet<>();
            for (ActivationCode c : codesOf(s)) mine.add(c.getId());
            if (mine.isEmpty()) return View.EMPTY;
            List<ActivationRecord> pool = new ArrayList<>();
            for (List<ActivationRecord> day : top(cutoff).kept().values()) {
                for (ActivationRecord r : day) if (mine.contains(r.getCodeId())) pool.add(r);
            }
            return credit(pool, cutoff, tiersOf(s), frozen(s), BY_CODE);
        }

        public View view(Agent a, LocalDateTime cutoff) {
            return a.isSub() ? sub(a, cutoff) : top(cutoff);
        }

        /** 被扣掉、代理看不到的有效激活（激活记录列表里不列）。从来没扣过量的不读记录 */
        public Set<Long> hidden(Agent a, LocalDateTime cutoff) {
            if (!deducts(top) && !(a.isSub() && deducts(a))) return Set.of();
            Set<Long> mine = null;
            if (a.isSub()) {
                mine = new HashSet<>();
                for (ActivationCode c : codesOf(a)) mine.add(c.getId());
            }
            Set<Long> kept = view(a, cutoff).ids();
            Set<Long> out = new HashSet<>();
            for (ActivationRecord r : records()) {
                if (mine != null && !mine.contains(r.getCodeId())) continue;
                if (confirmedAsOf(r, cutoff) && !kept.contains(r.getId())) out.add(r.getId());
            }
            return out;
        }

        private Map<Long, Long> subOfCode() {
            if (subOfCode == null) {
                subOfCode = new HashMap<>();
                for (ActivationCode c : codes()) if (c.getSubAgentId() != null) subOfCode.put(c.getId(), c.getSubAgentId());
            }
            return subOfCode;
        }
    }

    // ==================== 算 ====================

    private View credit(List<ActivationRecord> records, LocalDateTime cutoff, Deduction d, Frozen fz,
                        List<Function<ActivationRecord, Long>> levels) {
        Map<LocalDate, List<ActivationRecord>> byDay = new TreeMap<>();
        for (ActivationRecord r : records) {
            if (confirmedAsOf(r, cutoff)) byDay.computeIfAbsent(dayOf(r), k -> new ArrayList<>()).add(r);
        }
        int real = 0;
        Map<LocalDate, Fresh> fresh = new HashMap<>();
        for (Map.Entry<LocalDate, List<ActivationRecord>> e : byDay.entrySet()) {
            List<ActivationRecord> list = e.getValue();
            real += list.size();
            list.sort(ORDER);
            Set<Long> cut = deductDay(e.getKey(), list, d, fz, levels, fresh);
            if (!cut.isEmpty()) list.removeIf(r -> cut.contains(r.getId()));
        }
        return new View(byDay, real, fresh);
    }

    /** 一天里扣掉哪些：list 是这天的记录，按确认时间从早到晚。结算过的照结算表，没结算的这次算，记到 fresh 里 */
    private Set<Long> deductDay(LocalDate day, List<ActivationRecord> list, Deduction d, Frozen fz,
                                List<Function<ActivationRecord, Long>> levels, Map<LocalDate, Fresh> fresh) {
        Set<Long> out = new HashSet<>();
        List<Long> open = new ArrayList<>();
        for (ActivationRecord r : list) {
            Boolean h = fz.status(r.getId());
            if (h == null) open.add(r.getId());
            else if (h) out.add(r.getId());
        }
        if (open.isEmpty()) return out;
        Set<Long> cut = new HashSet<>();
        if (d.hasTiers()) {
            // 这天改过档位的，只算改了以后的这一段；档位按「到这时候当天一共有多少」选
            LocalDateTime from = fz.segFrom.get(day);
            List<ActivationRecord> seg = from == null ? list
                    : list.stream().filter(r -> fz.status(r.getId()) == null || time(r) == null || time(r).isAfter(from)).toList();
            int done = 0;
            for (ActivationRecord r : seg) if (Boolean.TRUE.equals(fz.status(r.getId()))) done++;
            int target = d.deducted(seg.size(), list.size());
            int need = Math.min(open.size(), Math.max(0, target - done));
            if (need > 0) allocate(seg, fz, target, need, levels, 0, cut);
        }
        out.addAll(cut);
        fresh.put(day, new Fresh(open, cut));
        return out;
    }

    /** 分扣量名额的一份 */
    private static final class Share {
        final List<ActivationRecord> recs = new ArrayList<>();
        /** 结算时已经扣掉的、还没结算的、这次分到的 */
        int done, open, count;
        /** (应扣 - 已扣) × 这一段的总数：越大越该先给 */
        long gap;
        /** 还没结算的里面最晚的一条 */
        ActivationRecord last;

        void add(ActivationRecord r, Boolean status) {
            recs.add(r);
            if (status == null) {
                open++;
                last = r;
            } else if (status) {
                done++;
            }
        }
    }

    private static final Comparator<Share> SPARE = Comparator.<Share>comparingLong(s -> s.gap).reversed()
            .thenComparing(s -> time(s.last), Comparator.nullsLast(Comparator.<LocalDateTime>reverseOrder()))
            .thenComparing(s -> s.last.getId(), Comparator.nullsLast(Comparator.<Long>reverseOrder()));

    /**
     * 在 seg（这一段的记录，按时间从早到晚）里还没结算的记录中扣 need 条，扣完这一段一共扣 target 条：
     * 按 levels[level] 分组，每组一共扣的和组的大小成比例（结算扣掉的算在里面、不再动），一条条给最该扣的组（最大余数法）；
     * 组里再按下一级分；分到最后一级时扣还没结算的里面最晚的那几条
     */
    static void allocate(List<ActivationRecord> seg, Frozen fz, int target, int need,
                         List<Function<ActivationRecord, Long>> levels, int level, Set<Long> out) {
        if (need <= 0 || seg.isEmpty()) return;
        if (level >= levels.size()) {
            for (int i = seg.size() - 1; i >= 0 && need > 0; i--) {
                ActivationRecord r = seg.get(i);
                if (fz.status(r.getId()) == null) {
                    out.add(r.getId());
                    need--;
                }
            }
            return;
        }
        Function<ActivationRecord, Long> key = levels.get(level);
        Map<Long, Share> groups = new LinkedHashMap<>();
        for (ActivationRecord r : seg) {
            Long k = key.apply(r);
            groups.computeIfAbsent(k == null ? 0L : k, x -> new Share()).add(r, fz.status(r.getId()));
        }
        if (groups.size() == 1) {
            allocate(seg, fz, target, need, levels, level + 1, out);
            return;
        }
        long n = seg.size();
        PriorityQueue<Share> queue = new PriorityQueue<>(SPARE);
        for (Share s : groups.values()) {
            s.gap = (long) target * s.recs.size() - (long) s.done * n;
            if (s.open > 0) queue.add(s);
        }
        for (int i = 0; i < need && !queue.isEmpty(); i++) {
            Share s = queue.poll();
            s.count++;
            s.gap -= n;
            if (s.count < s.open) queue.add(s);
        }
        for (Share s : groups.values()) {
            if (s.count > 0) allocate(s.recs, fz, s.done + s.count, s.count, levels, level + 1, out);
        }
    }
}
