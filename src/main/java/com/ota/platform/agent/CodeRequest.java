package com.ota.platform.agent;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 代理在代理后台申请口令：管理员同意后按申请生成口令，直接归属这个代理 */
@Entity
@Table(name = "agent_code_request", indexes = {
        @Index(name = "idx_code_request_agent", columnList = "agent_id"),
        @Index(name = "idx_code_request_status", columnList = "status")})
public class CodeRequest {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";
    public static final String CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    /** 申请的个数 */
    @Column(name = "request_count", nullable = false)
    private Integer count;

    /** 每个口令能激活几次，0 不限 */
    @Column(name = "max_uses", nullable = false)
    private Integer maxUses;

    /** 代理写的用途 */
    @Column(length = 200)
    private String note;

    @Column(nullable = false, length = 16)
    private String status = PENDING;

    /** 实际生成的个数（管理员同意时可以改） */
    @Column(name = "code_count")
    private Integer codeCount;

    /** 拒绝原因或审核备注 */
    @Column(name = "review_note", length = 200)
    private String reviewNote;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /** 自定义口令：代理填了要哪些口令（见 CodeRequestItem）；否则随机生成 */
    private Boolean custom = false;

    /** 代理设置成「自动通过」，没经过人工审核 */
    @Column(name = "auto_approved")
    private Boolean auto = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected CodeRequest() {}

    public CodeRequest(Long agentId, int count, int maxUses, String note, boolean custom) {
        this.agentId = agentId;
        this.count = count;
        this.maxUses = maxUses;
        this.note = note;
        this.custom = custom;
    }

    public Long getId() { return id; }
    public Long getAgentId() { return agentId; }
    public int getCount() { return count == null ? 0 : count; }
    public int getMaxUses() { return maxUses == null ? 0 : maxUses; }
    public String getNote() { return note; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getCodeCount() { return codeCount; }
    public void setCodeCount(Integer codeCount) { this.codeCount = codeCount; }
    public String getReviewNote() { return reviewNote; }
    public void setReviewNote(String reviewNote) { this.reviewNote = reviewNote; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
    public boolean isAuto() { return Boolean.TRUE.equals(auto); }
    public void setAuto(boolean auto) { this.auto = auto; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public boolean isPending() { return PENDING.equals(status); }
    public boolean isCustom() { return Boolean.TRUE.equals(custom); }
    public void setCount(int count) { this.count = count; }
}
