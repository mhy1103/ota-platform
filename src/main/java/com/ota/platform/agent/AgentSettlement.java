package com.ota.platform.agent;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 扣量结算：一个代理（一级、二级都算）一天一行，记下这天结算过的有效激活和其中扣掉、代理看不到的。
 * 结算过的记录只往里加，不再改；没结算的（今天的、还没到结算时间的）按现在的档位算。
 * 二级代理这里只记 TA 自己扣的，上级扣掉的看上级那行
 */
@Entity
@Table(name = "agent_settlement", uniqueConstraints = @UniqueConstraint(name = "uk_settlement_agent_day", columnNames = {"agent_id", "stat_day"}),
        indexes = @Index(name = "idx_settlement_until", columnList = "agent_id,settled_to"))
public class AgentSettlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "stat_day", nullable = false)
    private LocalDate day;

    /** 写这一行时结算到的时刻。一个代理所有行里最晚的那个，就是 TA 结算到哪儿了 */
    @Column(name = "settled_to", nullable = false)
    private LocalDateTime settledTo;

    /** 这天改过扣量的最后时刻：之后的激活按新档位单独算，之前的已经结算 */
    @Column(name = "seg_from")
    private LocalDateTime segFrom;

    /** 结算过的有效激活记录 ID，逗号分开 */
    @Column(name = "record_ids", columnDefinition = "MEDIUMTEXT")
    private String recordIds = "";

    /** 其中扣掉的 */
    @Column(name = "hidden_ids", columnDefinition = "MEDIUMTEXT")
    private String hiddenIds = "";

    /** 结算过的有多少、代理看到多少，查数用 */
    @Column(name = "real_count", nullable = false)
    private int realCount;

    @Column(name = "shown_count", nullable = false)
    private int shownCount;

    /** 升级时按旧版的算法写的 */
    @Column(nullable = false)
    private boolean legacy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    protected AgentSettlement() {}

    public AgentSettlement(Long agentId, LocalDate day, LocalDateTime settledTo, boolean legacy) {
        this.agentId = agentId;
        this.day = day;
        this.settledTo = settledTo;
        this.legacy = legacy;
    }

    public Long getId() { return id; }
    public Long getAgentId() { return agentId; }
    public LocalDate getDay() { return day; }
    public LocalDateTime getSettledTo() { return settledTo; }
    public LocalDateTime getSegFrom() { return segFrom; }
    public int getRealCount() { return realCount; }
    public int getShownCount() { return shownCount; }
    public boolean isLegacy() { return legacy; }

    public List<Long> recordIds() { return ids(recordIds); }
    public Set<Long> hiddenIds() { return new HashSet<>(ids(hiddenIds)); }

    /** 结算一批：ids 是这批的记录，hidden 是其中扣掉的 */
    public void add(Collection<Long> ids, Collection<Long> hidden) {
        if (ids.isEmpty()) return;
        recordIds = join(recordIds, ids);
        hiddenIds = join(hiddenIds, hidden);
        realCount += ids.size();
        shownCount += ids.size() - hidden.size();
        updatedAt = LocalDateTime.now();
    }

    public void settledTo(LocalDateTime t) {
        if (settledTo == null || t.isAfter(settledTo)) {
            settledTo = t;
            updatedAt = LocalDateTime.now();
        }
    }

    public void segFrom(LocalDateTime t) {
        segFrom = t;
        updatedAt = LocalDateTime.now();
    }

    private static List<Long> ids(String s) {
        if (s == null || s.isEmpty()) return List.of();
        List<Long> out = new ArrayList<>();
        for (String p : s.split(",")) if (!p.isEmpty()) out.add(Long.parseLong(p));
        return out;
    }

    private static String join(String old, Collection<Long> more) {
        if (more.isEmpty()) return old == null ? "" : old;
        StringBuilder sb = new StringBuilder(old == null ? "" : old);
        for (Long id : new TreeSet<>(more)) {
            if (sb.length() > 0) sb.append(',');
            sb.append(id);
        }
        return sb.toString();
    }
}
