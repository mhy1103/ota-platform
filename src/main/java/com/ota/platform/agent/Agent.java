package com.ota.platform.agent;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent", indexes = @Index(name = "idx_agent_parent", columnList = "parent_id"))
public class Agent {

    /** 数据更新方式 */
    public static final String MODE_REALTIME = "REALTIME";
    public static final String MODE_DAILY = "DAILY";
    public static final String MODE_MANUAL = "MANUAL";

    /** 申请口令：不能申请 / 申请后等管理员审核 / 自动通过 */
    public static final String REQUEST_OFF = "OFF";
    public static final String REQUEST_REVIEW = "REVIEW";
    public static final String REQUEST_AUTO = "AUTO";
    public static final int DEFAULT_DAILY_LIMIT = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 登录账号 */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    /** BCrypt 哈希 */
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** 显示名称 */
    @Column(nullable = false, length = 64)
    private String name;

    private String remark;

    private Boolean enabled = true;

    /** true：可查看设备记录；false：只看数量 */
    @Column(name = "can_view_records")
    private Boolean canViewRecords = false;

    /** REALTIME / DAILY / MANUAL */
    @Column(name = "data_mode", length = 16)
    private String dataMode = MODE_REALTIME;

    /** 手动更新模式下，代理能看到的数据截止时间 */
    @Column(name = "snapshot_at")
    private LocalDateTime snapshotAt;

    /**
     * 扣量档位，JSON 数组：[{"min":1,"max":10,"ratio":0},{"min":11,"max":50,"ratio":20}]
     * max 为 null 或 0 表示不限（该档到无穷大）
     */
    @Column(name = "deduction_tiers", length = 2000)
    private String deductionTiers;

    @Column(length = 2000)
    private String prefs;

    /** OFF / REVIEW / AUTO */
    @Column(name = "request_mode", length = 10)
    private String requestMode = REQUEST_OFF;

    /** 每天最多申请多少个口令（待审核和已通过的都算），0 不限 */
    @Column(name = "daily_request_limit")
    private Integer dailyRequestLimit = DEFAULT_DAILY_LIMIT;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    /** 二级代理的上级代理；一级代理为 null。只有两级，二级代理下面不能再开 */
    @Column(name = "parent_id")
    private Long parentId;

    /** 一级代理能不能开二级代理（管理员给） */
    @Column(name = "sub_agents")
    private Boolean subAgents = false;

    /** 最多开几个二级代理，0 不限 */
    @Column(name = "sub_agent_limit")
    private Integer subAgentLimit = 0;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public Boolean getCanViewRecords() { return canViewRecords; }
    public void setCanViewRecords(Boolean canViewRecords) { this.canViewRecords = canViewRecords; }
    public String getDataMode() { return dataMode == null ? MODE_REALTIME : dataMode; }
    public void setDataMode(String dataMode) { this.dataMode = dataMode; }
    public LocalDateTime getSnapshotAt() { return snapshotAt; }
    public void setSnapshotAt(LocalDateTime snapshotAt) { this.snapshotAt = snapshotAt; }
    public String getPrefs() { return prefs; }
    public void setPrefs(String prefs) { this.prefs = prefs; }
    public String getDeductionTiers() { return deductionTiers; }
    public void setDeductionTiers(String deductionTiers) { this.deductionTiers = deductionTiers; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getRequestMode() { return requestMode == null ? REQUEST_OFF : requestMode; }
    public void setRequestMode(String requestMode) { this.requestMode = requestMode; }
    public int getDailyRequestLimit() { return dailyRequestLimit == null ? DEFAULT_DAILY_LIMIT : dailyRequestLimit; }
    public void setDailyRequestLimit(Integer dailyRequestLimit) { this.dailyRequestLimit = dailyRequestLimit; }
    /** 二级代理只用上级分给的口令，不能自己申请 */
    public boolean canRequest() { return parentId == null && !REQUEST_OFF.equals(getRequestMode()); }
    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }
    public boolean isSub() { return parentId != null; }
    public Boolean getSubAgents() { return subAgents; }
    public void setSubAgents(Boolean subAgents) { this.subAgents = subAgents; }
    public boolean canManageSubs() { return parentId == null && Boolean.TRUE.equals(subAgents); }
    public int getSubAgentLimit() { return subAgentLimit == null ? 0 : subAgentLimit; }
    public void setSubAgentLimit(Integer subAgentLimit) { this.subAgentLimit = subAgentLimit; }
}
