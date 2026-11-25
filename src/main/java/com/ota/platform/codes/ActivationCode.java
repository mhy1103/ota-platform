package com.ota.platform.codes;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activation_code", uniqueConstraints = @UniqueConstraint(name = "uk_activation_code", columnNames = "code"),
        indexes = {@Index(name = "idx_code_agent", columnList = "agent_id"), @Index(name = "idx_code_sub_agent", columnList = "sub_agent_id")})
public class ActivationCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 口令内容，如 88888 */
    @Column(nullable = false, length = 64)
    private String code;

    /** 备注（代理名称等） */
    private String remark;

    /** 最大激活次数，0=不限 */
    @Column(name = "max_uses")
    private Integer maxUses = 0;

    /** 已有效激活次数 */
    @Column(name = "used_count")
    private Integer usedCount = 0;

    /**
     * 拿着这个口令的代理看到的有效激活次数（扣掉的不算；分给了二级代理的按二级代理看到的），次数上限按它算，代理看不出扣了量。
     * 没分给代理、这一家没扣过量时为空，和 usedCount 一样。由 CodeLimits 算好单独写，保存口令时不动它
     */
    @Column(name = "seen_count", insertable = false, updatable = false)
    private Integer seenCount;

    /** 是否启用 */
    private Boolean enabled = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 所属代理，null 表示未分配 */
    @Column(name = "agent_id")
    private Long agentId;

    /** 代理分给了自己的哪个二级代理；口令仍归 agentId 那个代理，统计、扣量都先算在 TA 名下 */
    @Column(name = "sub_agent_id")
    private Long subAgentId;

    /** 绑定的渠道：兑换成功后告诉 App 切到这个渠道，null 表示不绑定 */
    @Column(length = 40)
    private String channel;

    /** 代理申请通过后生成的口令：对应的申请 */
    @Column(name = "request_id")
    private Long requestId;

    // Getters & Setters
    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Integer getMaxUses() { return maxUses == null ? 0 : maxUses; }
    public void setMaxUses(Integer maxUses) { this.maxUses = maxUses; }
    public Integer getUsedCount() { return usedCount == null ? 0 : usedCount; }
    public void setUsedCount(Integer usedCount) { this.usedCount = usedCount; }
    public Integer getSeenCount() { return seenCount; }
    /** 算在次数上限里的：代理看到的，没有就是实际的 */
    public int limitUsed() { return seenCount != null ? seenCount : getUsedCount(); }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public Long getAgentId() { return agentId; }
    /** 换了归属的代理，原来分给的二级代理就不算了 */
    public void setAgentId(Long agentId) {
        if (agentId == null || !agentId.equals(this.agentId)) this.subAgentId = null;
        this.agentId = agentId;
    }
    public Long getSubAgentId() { return subAgentId; }
    public void setSubAgentId(Long subAgentId) { this.subAgentId = subAgentId; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }

    /** 是否还能激活（次数按代理看到的算） */
    public boolean canActivate() {
        if (!Boolean.TRUE.equals(enabled)) return false;
        if (expiresAt != null && LocalDateTime.now().isAfter(expiresAt)) return false;
        if (getMaxUses() > 0 && limitUsed() >= getMaxUses()) return false;
        return true;
    }
}
