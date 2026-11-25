package com.ota.platform.web.agent;

import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.codes.ActivationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 口令的次数上限按代理看到的算：扣掉的激活不占次数，口令一直能用到代理看到的满了为止，代理那边「用了几次、还能不能用」
 * 和 TA 看到的有效激活对得上，看不出扣了量。拿着口令的是二级代理时按二级代理看到的算（不管 TA 那边的截止时间，算到现在）。
 * 每个口令代理看到了几次存在 activation_code.seen_count：激活确认了、口令换了代理、结算、改扣量以后在后台重新算那一家的；
 * 兑换时实际次数已经到上限的，当场再算一遍
 */
@Service
public class CodeLimits {

    private static final Logger log = LoggerFactory.getLogger(CodeLimits.class);
    /** 算过一遍了（设置里有这一项）：之后启动不用先算完 */
    static final String READY = "codes.seen.ready";

    private final AgentRepo agents;
    private final ActivationCodeRepo codes;
    private final AgentLedger ledger;
    private final com.ota.platform.settings.SettingService settings;
    private final TransactionTemplate tx;
    private final Set<Long> dirty = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean queued = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "code-limits");
        t.setDaemon(true);
        return t;
    });

    public CodeLimits(AgentRepo agents, ActivationCodeRepo codes, AgentLedger ledger, com.ota.platform.settings.SettingService settings,
                      PlatformTransactionManager tm) {
        this.agents = agents;
        this.codes = codes;
        this.ledger = ledger;
        this.settings = settings;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 兑换时：这个口令还能不能用。实际次数没到上限的直接能用（代理看到的只会更少），到了的现算一遍代理看到了几次 */
    public boolean canActivate(ActivationCode c) {
        if (!Boolean.TRUE.equals(c.getEnabled())) return false;
        if (c.getExpiresAt() != null && LocalDateTime.now().isAfter(c.getExpiresAt())) return false;
        int max = c.getMaxUses();
        if (max <= 0 || c.getUsedCount() < max) return true;
        Agent top = c.getAgentId() == null ? null : agents.findById(c.getAgentId()).orElse(null);
        if (top == null || top.isSub()) return false;
        List<Agent> subs = agents.findByParentIdOrderByCreatedAtDesc(top.getId());
        Integer seen = seen(ledger.family(top, null).preload(subs), top, subs).get(c.getId());
        if (!Objects.equals(seen, c.getSeenCount())) touch(top.getId());
        return seen != null && seen < max;
    }

    /** 这一家（一级代理 ID）的口令次数要重新算；在调用方的事务提交以后、后台慢慢算 */
    public void touch(Long topId) {
        if (topId == null) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    queue(topId);
                }
            });
        } else {
            queue(topId);
        }
    }

    private void queue(Long topId) {
        dirty.add(topId);
        if (queued.compareAndSet(false, true)) worker.submit(this::drain);
    }

    private void drain() {
        queued.set(false);
        for (Long id : new ArrayList<>(dirty)) {
            dirty.remove(id);
            try {
                refresh(id);
            } catch (Exception e) {
                log.warn("重算口令 {} 家的次数失败：{}", id, e.toString());
            }
        }
        try {
            tx.executeWithoutResult(st -> codes.clearUnowned());
        } catch (Exception e) {
            log.warn("清不归代理的口令次数失败：{}", e.toString());
        }
    }

    /**
     * 启动时：升级上来、从来没算过的，先算完再对外服务（代理一打开就是对的）；
     * 之后每次重启，存着的次数本来就是对的，放到后台再核一遍，不耽误启动
     */
    public void startup() {
        if (settings.get(READY, null) == null) {
            refreshAll();
            settings.set(READY, "1");
        } else {
            worker.submit(() -> {
                try {
                    refreshAll();
                } catch (Exception e) {
                    log.warn("启动后重算口令次数失败，10 分钟后再算：{}", e.toString());
                }
            });
        }
    }

    /** 全部重算：每 10 分钟兜底一次（恢复备份、直接改库这类没通知到的） */
    @Scheduled(initialDelay = 600_000, fixedDelay = 600_000)
    public void refreshAll() {
        for (Agent a : agents.findByParentIdIsNullOrderByCreatedAtDesc()) {
            try {
                refresh(a.getId());
            } catch (Exception e) {
                log.warn("重算代理 {} 的口令次数失败：{}", a.getName(), e.toString());
            }
        }
        tx.executeWithoutResult(st -> codes.clearUnowned());
    }

    /** 重算一家每个口令代理看到了几次，变了的写回去 */
    public void refresh(Long topId) {
        tx.executeWithoutResult(st -> {
            Agent top = agents.findById(topId).orElse(null);
            if (top == null || top.isSub()) return;
            List<Agent> subs = agents.findByParentIdOrderByCreatedAtDesc(topId);
            AgentLedger.Family f = ledger.family(top, null).preload(subs);
            Map<Long, Integer> seen = seen(f, top, subs);
            for (ActivationCode c : f.codes()) {
                Integer v = seen.get(c.getId());
                if (!Objects.equals(v, c.getSeenCount())) codes.updateSeen(c.getId(), v);
            }
        });
    }

    /** 每个口令拿着它的代理（分给了二级代理是二级代理，否则一级代理）到现在看到了几次；这一家从来没扣过量时都是空 */
    private static Map<Long, Integer> seen(AgentLedger.Family f, Agent top, List<Agent> subs) {
        Map<Long, Integer> out = new HashMap<>();
        boolean deducts = f.deducts(top);
        for (Agent s : subs) deducts |= f.deducts(s);
        if (!deducts) return out;
        Map<Long, Map<Long, Integer>> by = new HashMap<>();
        by.put(top.getId(), perCode(f.top(null)));
        Map<Long, Agent> subById = new HashMap<>();
        for (Agent s : subs) subById.put(s.getId(), s);
        for (ActivationCode c : f.codes()) {
            Agent holder = c.getSubAgentId() == null ? null : subById.get(c.getSubAgentId());
            Long key = holder == null ? top.getId() : holder.getId();
            if (holder != null && !by.containsKey(key)) by.put(key, perCode(f.sub(holder, null)));
            out.put(c.getId(), by.get(key).getOrDefault(c.getId(), 0));
        }
        return out;
    }

    private static Map<Long, Integer> perCode(AgentLedger.View v) {
        Map<Long, Integer> m = new HashMap<>();
        for (List<ActivationRecord> day : v.kept().values()) for (ActivationRecord r : day) m.merge(r.getCodeId(), 1, Integer::sum);
        return m;
    }
}
