package com.ota.platform.audit;

import com.ota.platform.web.AdminUi;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "operation_log", indexes = {
        @Index(name = "idx_log_created", columnList = "created_at"),
        @Index(name = "idx_log_app", columnList = "app_id,created_at")})
public class OperationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 操作类型，见 AdminUi 里的标签表 */
    @Column(nullable = false)
    private String action;

    /** 操作详情 */
    @Column(length = 500)
    private String detail;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 谁操作的：管理员或代理的账号、「令牌：名称」；设备事件为空 */
    @Column(length = 64)
    private String operator;

    /** 操作的 App，系统级操作为空 */
    @Column(name = "app_id")
    private Long appId;

    public OperationLog() {}

    public OperationLog(String action, String detail, String operator, Long appId) {
        this.action = action;
        this.detail = detail;
        this.operator = operator;
        this.appId = appId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getAction() { return action; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getOperator() { return operator; }
    public Long getAppId() { return appId; }
}
