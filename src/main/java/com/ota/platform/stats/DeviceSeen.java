package com.ota.platform.stats;

import jakarta.persistence.*;

import java.time.Instant;

/** 检查过更新的设备，记下它当前运行的版本，用于版本分布统计。 */
@Entity
@Table(name = "ota_device", uniqueConstraints = @UniqueConstraint(columnNames = {"app_id", "client_id"}),
        indexes = @Index(name = "idx_device_app_seen", columnList = "app_id,last_seen"))
public class DeviceSeen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(length = 16)
    private String platform;

    @Column(name = "runtime_version", length = 64)
    private String runtimeVersion;

    @Column(length = 40)
    private String channel;

    @Column(name = "current_update_id", length = 64)
    private String currentUpdateId;

    /** 最近一次发给它的版本，用来统计推送数（同一版本只算一次） */
    @Column(name = "offered_update_id", length = 64)
    private String offeredUpdateId;

    /** 它上次报告的启动失败版本（逗号分隔），新出现的才计入失败数 */
    @Column(name = "failed_update_ids", length = 400)
    private String failedUpdateIds;

    @Column(name = "first_seen", nullable = false)
    private Instant firstSeen = Instant.now();

    @Column(name = "last_seen", nullable = false)
    private Instant lastSeen = Instant.now();

    protected DeviceSeen() {}

    public DeviceSeen(Long appId, String clientId) {
        this.appId = appId;
        this.clientId = clientId;
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public String getClientId() { return clientId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getRuntimeVersion() { return runtimeVersion; }
    public void setRuntimeVersion(String runtimeVersion) { this.runtimeVersion = runtimeVersion; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getCurrentUpdateId() { return currentUpdateId; }
    public void setCurrentUpdateId(String currentUpdateId) { this.currentUpdateId = currentUpdateId; }
    public String getOfferedUpdateId() { return offeredUpdateId; }
    public void setOfferedUpdateId(String offeredUpdateId) { this.offeredUpdateId = offeredUpdateId; }
    public String getFailedUpdateIds() { return failedUpdateIds; }
    public void setFailedUpdateIds(String failedUpdateIds) { this.failedUpdateIds = failedUpdateIds; }
    public Instant getFirstSeen() { return firstSeen; }
    public Instant getLastSeen() { return lastSeen; }
    public void setLastSeen(Instant lastSeen) { this.lastSeen = lastSeen; }
}
