package com.ota.platform.web.agent;

import com.ota.platform.agent.Agent;
import com.ota.platform.agent.Deduction;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.codes.ActivationRecord;
import com.ota.platform.codes.ActivationRecordRepo;
import com.ota.platform.web.AdminUi;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 有结算表以前（2026-11-20 那版）代理后台的扣量算法，原样搬过来：每次都按代理现在的档位把所有日子重算一遍，
 * 每天扣最晚确认的那几条；二级代理先去掉上级扣掉的，再按自己的档位扣。
 * 升级后第一次启动时用它算出代理当时看到的数，写进结算表，只用这一次
 */
final class LegacyLedger {

    private final ActivationCodeRepo codeRepo;
    private final ActivationRecordRepo recordRepo;
    private final AdminUi ui;
    private final Map<Long, String> tiers;

    /** tiers：这些代理不按现在的档位，按这里的算（刚改了档位，按改之前的） */
    LegacyLedger(ActivationCodeRepo codeRepo, ActivationRecordRepo recordRepo, AdminUi ui, Map<Long, String> tiers) {
        this.codeRepo = codeRepo;
        this.recordRepo = recordRepo;
        this.ui = ui;
        this.tiers = tiers;
    }

    private Deduction tiersOf(Agent a) {
        return Deduction.parse(tiers.containsKey(a.getId()) ? tiers.get(a.getId()) : a.getDeductionTiers());
    }

    /** 代理能看到的数据截止时间；null 是实时。二级代理取和上级里早的那个 */
    LocalDateTime cutoff(Agent a, Agent parent) {
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

    private static LocalDateTime earlier(LocalDateTime x, LocalDateTime y) {
        if (x == null) return y;
        if (y == null) return x;
        return y.isBefore(x) ? y : x;
    }

    private String mode(Agent a, Agent p) {
        if (p == null) return a.getDataMode();
        LocalDateTime own = ownCutoff(a), up = ownCutoff(p);
        if (own == null && up == null) return Agent.MODE_REALTIME;
        return own == null || (up != null && up.isBefore(own)) ? p.getDataMode() : a.getDataMode();
    }

    /** 没有时间的老记录算在哪天：每日 0 点是昨天，手动是截止那天，实时是今天 */
    private LocalDate refDay(Agent a, Agent p, LocalDateTime cutoff) {
        LocalDate today = ui.today();
        return switch (mode(a, p)) {
            case Agent.MODE_DAILY -> today.minusDays(1);
            case Agent.MODE_MANUAL -> ui.localDate(cutoff);
            default -> today;
        };
    }

    /** 一级代理是归 TA 的全部口令（含分给二级代理的），二级代理是上级分给 TA 的 */
    List<ActivationCode> codesOf(Agent a) {
        if (!a.isSub()) return codeRepo.findByAgentIdOrderByCreatedAtDesc(a.getId());
        return codeRepo.findBySubAgentIdOrderByCreatedAtDesc(a.getId()).stream()
                .filter(c -> a.getParentId().equals(c.getAgentId())).toList();
    }

    List<ActivationRecord> records(Agent a) {
        List<Long> ids = codesOf(a).stream().map(ActivationCode::getId).toList();
        return ids.isEmpty() ? List.of() : recordRepo.findByCodeIdIn(ids);
    }

    /** 一级代理看不到的有效激活 */
    Set<Long> hidden(Agent top) {
        Deduction d = tiersOf(top);
        if (!d.hasTiers()) return Set.of();
        List<ActivationRecord> mine = records(top);
        if (mine.isEmpty()) return Set.of();
        LocalDateTime cutoff = cutoff(top, null);
        return dropped(mine, cutoff, d, refDay(top, null, cutoff));
    }

    /** 二级代理看不到的有效激活：上级扣掉的（upHidden），加上 TA 按自己的档位扣的 */
    Set<Long> hidden(Agent s, Agent top, Set<Long> upHidden) {
        boolean upTiers = tiersOf(top).hasTiers();
        Deduction d = tiersOf(s);
        if (!upTiers && !d.hasTiers()) return Set.of();
        List<ActivationRecord> mine = records(s);
        if (mine.isEmpty()) return Set.of();
        Set<Long> out = new HashSet<>();
        if (upTiers) for (ActivationRecord r : mine) if (upHidden.contains(r.getId())) out.add(r.getId());
        if (d.hasTiers()) {
            List<ActivationRecord> pool = mine.stream().filter(r -> !out.contains(r.getId())).toList();
            LocalDateTime cutoff = cutoff(s, top);
            out.addAll(dropped(pool, cutoff, d, refDay(s, top, cutoff)));
        }
        return out;
    }

    /** 截止时间内确认的按天分开，每天按时间排好，留前面「扣量后的数」条，后面的扣掉 */
    private Set<Long> dropped(List<ActivationRecord> records, LocalDateTime cutoff, Deduction d, LocalDate fallbackDay) {
        Map<LocalDate, List<ActivationRecord>> byDay = new TreeMap<>();
        for (ActivationRecord r : records) {
            if (!AgentLedger.confirmedAsOf(r, cutoff)) continue;
            LocalDateTime t = AgentLedger.time(r);
            byDay.computeIfAbsent(t != null ? ui.localDate(t) : fallbackDay, k -> new ArrayList<>()).add(r);
        }
        Set<Long> out = new HashSet<>();
        for (List<ActivationRecord> day : byDay.values()) {
            day.sort(Comparator.comparing(AgentLedger::time, Comparator.nullsFirst(Comparator.<LocalDateTime>naturalOrder())));
            for (ActivationRecord r : day.subList(d.credited(day.size()), day.size())) out.add(r.getId());
        }
        return out;
    }
}
