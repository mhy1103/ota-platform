package com.ota.platform.notice;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 代理看过哪条公告 */
@Entity
@Table(name = "agent_notice_read", uniqueConstraints = @UniqueConstraint(columnNames = {"notice_id", "agent_id"}))
public class NoticeRead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "notice_id", nullable = false)
    private Long noticeId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "read_at", nullable = false)
    private LocalDateTime readAt = LocalDateTime.now();

    protected NoticeRead() {}

    public NoticeRead(Long noticeId, Long agentId) {
        this.noticeId = noticeId;
        this.agentId = agentId;
    }

    public Long getNoticeId() { return noticeId; }
    public Long getAgentId() { return agentId; }
    public LocalDateTime getReadAt() { return readAt; }
}
