package com.ota.platform.web.agent;

import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.agent.AgentSettlement;
import com.ota.platform.agent.AgentSettlementRepo;
import com.ota.platform.agent.Deduction;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.codes.ActivationRecord;
import com.ota.platform.codes.ActivationRecordRepo;
import com.ota.platform.web.AdminUi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 扣量结算：到了代理的结算时间，把 TA 能看到的有效激活怎么扣的写进结算表，之后不再变。
 * 实时和每日 0 点的代理每天 0 点结算前一天的，手动更新的代理点更新时结算到更新的时刻；
 * 改扣量档位时先按原来的档位结算到改的那一刻，之后的激活按新档位算。
 * 升级上来、从没结算过的代理，启动时按旧版的算法把 TA 当时看到的数写进去（只补这一次），升级不会改动已经扣过的数
 */
@Service
public class Settlement implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(Settlement.class);

    private final AgentRepo agents;
    private final AgentSettlementRepo repo;
    private final ActivationCodeRepo codeRepo;
    private final ActivationRecordRepo recordRepo;
    private final AgentLedger ledger;
    private final CodeLimits limits;
    private final AdminUi ui;
    private final TransactionTemplate tx;

    public Settlement(AgentRepo agents, AgentSettlementRepo repo, ActivationCodeRepo codeRepo, ActivationRecordRepo recordRepo,
                      AgentLedger ledger, CodeLimits limits, AdminUi ui, PlatformTransactionManager tm) {
        this.agents = agents;
        this.repo = repo;
        this.codeRepo = codeRepo;
        this.recordRepo = recordRepo;
        this.ledger = ledger;
        this.limits = limits;
        this.ui = ui;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 在 Web 服务开始接请求之前跑完：代理打开后台时，升级前的数已经写死了，口令的次数也是按 TA 看到的 */
    @Override
    public void afterSingletonsInstantiated() {
        try {
            settleDue();
        } catch (Exception e) {
            log.warn("启动时扣量结算没做完，每分钟会再试：{}", e.toString());
        }
        try {
            limits.startup();
        } catch (Exception e) {
            log.warn("启动时算口令次数没做完，10 分钟后再算：{}", e.toString());
        }
    }

    /** 过了 0 点、手动代理更新了，一分钟内结算 */
    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void tick() {
        try {
            settleDue();
        } catch (Exception e) {
            log.warn("扣量结算失败：{}", e.toString());
        }
    }

    /** 所有代理到点该结算的都结掉 */
    public synchronized void settleDue() {
        Map<Long, LocalDateTime> until = until();
        Map<Long, List<Agent>> subsOf = new HashMap<>();
        List<Agent> tops = new ArrayList<>();
        for (Agent a : agents.findAll()) {
            if (a.isSub()) subsOf.computeIfAbsent(a.getParentId(), k -> new ArrayList<>()).add(a);
            else tops.add(a);
        }
        LocalDateTime now = LocalDateTime.now();
        int legacy = 0;
        for (Agent top : tops) {
            try {
                if (family(top, subsOf.getOrDefault(top.getId(), List.of()), until, now)) legacy++;
            } catch (Exception e) {
                log.warn("代理 {} 的扣量结算失败：{}", top.getName(), e.toString());
            }
        }
        if (legacy > 0) log.info("扣量结算：{} 个代理是升级上来的，已按升级前的算法把 TA 们现在看到的数写死", legacy);
    }

    /** 一家（一级代理和 TA 的二级代理）到点的结掉；从没结算过的按旧版的算法补，返回是不是补的 */
    private boolean family(Agent top, List<Agent> subs, Map<Long, LocalDateTime> until, LocalDateTime now) {
        if (!until.containsKey(top.getId())) {
            legacy(top, subs, now, Map.of());
            return true;
        }
        Map<Long, LocalDateTime> due = due(top, subs);
        boolean behind = false;
        for (Map.Entry<Long, LocalDateTime> e : due.entrySet()) {
            LocalDateTime w = until.get(e.getKey());
            if (w == null || w.isBefore(e.getValue())) behind = true;
        }
        if (behind) run(top, subs, due, null, null);
        return false;
    }

    /** 每个代理该结算到的时刻：实时和每日 0 点是今天 0 点，手动是上次更新的时间；二级代理不超过上级的 */
    private Map<Long, LocalDateTime> due(Agent top, List<Agent> subs) {
        Map<Long, LocalDateTime> m = new HashMap<>();
        LocalDateTime up = ownDue(top);
        m.put(top.getId(), up);
        for (Agent s : subs) {
            LocalDateTime d = ownDue(s);
            m.put(s.getId(), d.isBefore(up) ? d : up);
        }
        return m;
    }

    private LocalDateTime ownDue(Agent a) {
        if (Agent.MODE_MANUAL.equals(a.getDataMode())) {
            LocalDateTime t = a.getSnapshotAt() != null ? a.getSnapshotAt() : a.getCreatedAt();
            if (t != null) return t;
        }
        return ui.startOfToday();
    }

    private Map<Long, LocalDateTime> until() {
        Map<Long, LocalDateTime> m = new HashMap<>();
        for (Object[] r : repo.settledTo()) m.put((Long) r[0], (LocalDateTime) r[1]);
        return m;
    }

    // ==================== 什么时候结算 ====================

    /** 新开的代理：从开通这一刻起算，以后不会再按旧版的算法补 */
    public void created(Agent a) {
        LocalDateTime t = a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.now();
        repo.save(new AgentSettlement(a.getId(), ui.localDate(t), t, false));
    }

    /**
     * 保存代理以后调用，before 是改之前的档位。档位真的变了：先按原来的档位把到现在为止的激活结算掉（一级代理的也结掉），
     * 之后的激活按新档位单独算。返回档位是不是变了
     */
    public boolean tiersChanged(Agent a, String before) {
        if (Objects.equals(Deduction.normalize(before), Deduction.normalize(a.getDeductionTiers()))) return false;
        LocalDateTime at = LocalDateTime.now();
        Long id = a.getId(), topId = a.isSub() ? a.getParentId() : a.getId();
        afterCommit(() -> changed(topId, id, before, at));
        return true;
    }

    private synchronized void changed(Long topId, Long id, String before, LocalDateTime at) {
        Agent top = agents.findById(topId).orElse(null);
        if (top == null) return;
        List<Agent> subs = agents.findByParentIdOrderByCreatedAtDesc(topId);
        Map<Long, String> old = new HashMap<>();
        old.put(id, before);
        if (!until().containsKey(topId)) legacy(top, subs, at, old);
        Map<Long, LocalDateTime> upTo = due(top, subs);
        upTo.put(topId, at);
        upTo.put(id, at);
        run(top, subs, upTo, old, at);
    }

    /** 手动更新了数据、改了数据更新方式以后：把这一家到点的结掉 */
    public void settle(Agent a) {
        Long topId = a.isSub() ? a.getParentId() : a.getId();
        afterCommit(() -> settleFamily(topId));
    }

    /** 一起更新了所有手动代理以后 */
    public void settleAll() {
        afterCommit(this::settleDue);
    }

    private synchronized void settleFamily(Long topId) {
        Agent top = agents.findById(topId).orElse(null);
        if (top == null || top.isSub()) return;
        family(top, agents.findByParentIdOrderByCreatedAtDesc(topId), until(), LocalDateTime.now());
    }

    /** 删代理时一起删 */
    public void forget(Collection<Long> agentIds) {
        if (!agentIds.isEmpty()) repo.deleteByAgentIds(agentIds);
    }

    /** 在调用方的事务提交以后做（读到的是保存好的代理），没有事务就马上做 */
    private void afterCommit(Runnable r) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safely(r);
                }
            });
        } else {
            safely(r);
        }
    }

    private void safely(Runnable r) {
        try {
            r.run();
        } catch (Exception e) {
            log.warn("扣量结算失败，到点会再结：{}", e.toString());
        }
    }

    // ==================== 结算 ====================

    /**
     * 把一家结算到 upTo（代理 ID → 结算到的时刻）：一级代理先结，二级代理在上级看到的里面再结。
     * before 里的代理按改之前的档位算，这天从 breakAt 起按新档位单独算
     */
    private void run(Agent top, List<Agent> subs, Map<Long, LocalDateTime> upTo, Map<Long, String> before, LocalDateTime breakAt) {
        tx.executeWithoutResult(st -> {
            List<Long> ids = new ArrayList<>(List.of(top.getId()));
            for (Agent s : subs) ids.add(s.getId());
            Map<Long, TreeMap<LocalDate, AgentSettlement>> rows = new HashMap<>();
            for (AgentSettlement s : repo.findByAgentIdIn(ids)) rows.computeIfAbsent(s.getAgentId(), k -> new TreeMap<>()).put(s.getDay(), s);
            AgentLedger.Family f = ledger.family(top, null).preload(subs);
            if (before != null) before.forEach((id, json) -> f.tiers(id, Deduction.parse(json)));
            LocalDateTime tp = upTo.get(top.getId());
            if (tp != null) {
                write(top.getId(), f.top(tp), tp, rows, before != null && before.containsKey(top.getId()) ? breakAt : null);
                f.reload(top);
            }
            for (Agent s : subs) {
                LocalDateTime ts = upTo.get(s.getId());
                if (ts != null) write(s.getId(), f.sub(s, ts), ts, rows, before != null && before.containsKey(s.getId()) ? breakAt : null);
            }
            limits.touch(top.getId());
        });
    }

    /** 这次新算的记录写进各天那行；没有新记录的，把结算到的时刻记在最近一行上 */
    private void write(Long agentId, AgentLedger.View v, LocalDateTime upTo, Map<Long, TreeMap<LocalDate, AgentSettlement>> rows,
                       LocalDateTime breakAt) {
        TreeMap<LocalDate, AgentSettlement> mine = rows.computeIfAbsent(agentId, k -> new TreeMap<>());
        Set<AgentSettlement> touched = new LinkedHashSet<>();
        for (Map.Entry<LocalDate, AgentLedger.Fresh> e : v.fresh().entrySet()) {
            AgentLedger.Fresh fr = e.getValue();
            if (fr.ids().isEmpty()) continue;
            AgentSettlement row = mine.computeIfAbsent(e.getKey(), d -> new AgentSettlement(agentId, d, upTo, false));
            row.add(fr.ids(), fr.hidden());
            touched.add(row);
        }
        if (breakAt != null) {
            AgentSettlement row = mine.computeIfAbsent(ui.localDate(breakAt), d -> new AgentSettlement(agentId, d, upTo, false));
            row.segFrom(breakAt);
            touched.add(row);
        }
        if (touched.isEmpty()) {
            if (mine.isEmpty()) mine.put(ui.localDate(upTo), new AgentSettlement(agentId, ui.localDate(upTo), upTo, false));
            touched.add(mine.lastEntry().getValue());
        }
        for (AgentSettlement row : touched) row.settledTo(upTo);
        repo.saveAll(touched);
    }

    /** 升级：按旧版的算法算出这一家每个代理现在看到的，写进结算表，结算到各自能看到的时间。tiers 里的代理按这套档位算 */
    private void legacy(Agent top, List<Agent> subs, LocalDateTime now, Map<Long, String> tiers) {
        tx.executeWithoutResult(st -> {
            LegacyLedger old = new LegacyLedger(codeRepo, recordRepo, ui, tiers);
            LocalDateTime cp = old.cutoff(top, null);
            Set<Long> upHidden = old.hidden(top);
            List<ActivationRecord> mine = old.records(top);
            frozen(top.getId(), mine, cp, upHidden, cp != null ? cp : now);
            for (Agent s : subs) {
                LocalDateTime cs = old.cutoff(s, top);
                Set<Long> hidden = old.hidden(s, top, upHidden);
                List<ActivationRecord> pool = old.records(s).stream().filter(r -> !upHidden.contains(r.getId())).toList();
                frozen(s.getId(), pool, cs, hidden, cs != null ? cs : now);
            }
            limits.touch(top.getId());
        });
    }

    private void frozen(Long agentId, List<ActivationRecord> records, LocalDateTime cutoff, Set<Long> hidden, LocalDateTime upTo) {
        Map<LocalDate, List<Long>> byDay = new TreeMap<>();
        for (ActivationRecord r : records) {
            if (AgentLedger.confirmedAsOf(r, cutoff)) byDay.computeIfAbsent(ledger.dayOf(r), k -> new ArrayList<>()).add(r.getId());
        }
        List<AgentSettlement> out = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Long>> e : byDay.entrySet()) {
            AgentSettlement row = new AgentSettlement(agentId, e.getKey(), upTo, true);
            row.add(e.getValue(), e.getValue().stream().filter(hidden::contains).toList());
            out.add(row);
        }
        if (out.isEmpty()) out.add(new AgentSettlement(agentId, ui.localDate(upTo), upTo, true));
        repo.saveAll(out);
    }
}
