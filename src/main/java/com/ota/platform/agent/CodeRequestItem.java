package com.ota.platform.agent;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** 自定义口令申请里的每一个口令：已存在、和别的申请撞了的提交时就自动拒绝；审核时可以只同意其中一部分 */
@Entity
@Table(name = "agent_code_request_item", indexes = {
        @Index(name = "idx_request_item_request", columnList = "request_id"),
        @Index(name = "idx_request_item_code", columnList = "code")})
public class CodeRequestItem {

    public static final String PENDING = "PENDING";
    public static final String APPROVED = "APPROVED";
    public static final String REJECTED = "REJECTED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 16)
    private String status = PENDING;

    /** 拒绝的原因：已存在、和别的申请重复、管理员没有同意…… */
    @Column(length = 100)
    private String reason;

    protected CodeRequestItem() {}

    public CodeRequestItem(Long requestId, String code, String status, String reason) {
        this.requestId = requestId;
        this.code = code;
        this.status = status;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public Long getRequestId() { return requestId; }
    public String getCode() { return code; }
    public String getStatus() { return status; }
    public String getReason() { return reason; }
    public boolean isPending() { return PENDING.equals(status); }

    public void reject(String why) {
        this.status = REJECTED;
        this.reason = why;
    }

    public void approve() {
        this.status = APPROVED;
        this.reason = null;
    }
}
