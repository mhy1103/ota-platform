package com.ota.platform.notice;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 给代理的公告，可以置顶，重要的在代理后台醒目显示。
 * 管理员发的：发给全部代理（含二级代理），或者只发一级代理；一级代理发的（ownerAgentId）：只发给 TA 自己的二级代理
 */
@Entity
@Table(name = "agent_notice", indexes = @Index(name = "idx_notice_created", columnList = "created_at"))
public class Notice {

    public static final String NORMAL = "normal";
    public static final String IMPORTANT = "important";
    /** 发给谁：全部代理（含二级代理）；只发一级代理 */
    public static final String ALL = "all";
    public static final String TOP = "top";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(nullable = false, length = 2000)
    private String body;

    @Column(nullable = false)
    private boolean pinned;

    @Column(nullable = false, length = 16)
    private String level = NORMAL;

    /** 管理员的公告发给谁；以前的公告是空的，算全部代理 */
    @Column(length = 8)
    private String audience;

    /** 一级代理发给自己的二级代理的：发的那个代理；管理员发的是 null */
    @Column(name = "owner_agent_id")
    private Long ownerAgentId;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Notice() {}

    public Notice(String title, String body, boolean pinned, String level, String createdBy) {
        this.createdBy = createdBy;
        edit(title, body, pinned, level);
        this.updatedAt = null;
    }

    public void edit(String title, String body, boolean pinned, String level) {
        this.title = title;
        this.body = body;
        this.pinned = pinned;
        this.level = IMPORTANT.equals(level) ? IMPORTANT : NORMAL;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public boolean isPinned() { return pinned; }
    public void setPinned(boolean pinned) { this.pinned = pinned; }
    public String getLevel() { return level; }
    public boolean isImportant() { return IMPORTANT.equals(level); }
    public String getAudience() { return TOP.equals(audience) ? TOP : ALL; }
    public void setAudience(String audience) { this.audience = TOP.equals(audience) ? TOP : ALL; }
    /** 二级代理也能看到（管理员发给全部代理的） */
    public boolean isForSubs() { return ownerAgentId == null && !TOP.equals(audience); }
    public Long getOwnerAgentId() { return ownerAgentId; }
    public void setOwnerAgentId(Long ownerAgentId) { this.ownerAgentId = ownerAgentId; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
