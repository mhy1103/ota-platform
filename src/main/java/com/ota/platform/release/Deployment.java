package com.ota.platform.release;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 某个渠道、某个 Runtime 当前的线上版本。
 * 灰度时只有 rolloutPercent 比例的设备拿到 releaseId，其余设备继续拿 previousReleaseId。
 * rollbackToEmbedded 为 true 时，设备收到「回退到安装包内置版本」的指令。
 * activationReleaseId 是单独设的口令更新包：还在用安装包内置版本的设备（新装的、正在输入口令激活的）检查更新时拿它，
 * 不灰度、不受回退内置版本影响；没设时这些设备和以前一样拿线上版本。
 */
@Entity
@Table(name = "ota_deployment", uniqueConstraints = @UniqueConstraint(columnNames = {"channel_id", "runtime_version"}))
public class Deployment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "channel_id", nullable = false)
    private Long channelId;

    @Column(name = "runtime_version", nullable = false, length = 64)
    private String runtimeVersion;

    @Column(name = "release_id", length = 36)
    private String releaseId;

    @Column(name = "rollout_percent", nullable = false)
    private int rolloutPercent = 100;

    @Column(name = "previous_release_id", length = 36)
    private String previousReleaseId;

    @Column(name = "rollback_to_embedded")
    private Boolean rollbackToEmbedded = false;

    @Column(name = "rollback_at")
    private Instant rollbackAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "activation_release_id", length = 36)
    private String activationReleaseId;

    @Column(name = "activation_at")
    private Instant activationAt;

    @Column(name = "activation_by", length = 100)
    private String activationBy;

    protected Deployment() {}

    public Deployment(Long channelId, String runtimeVersion) {
        this.channelId = channelId;
        this.runtimeVersion = runtimeVersion;
    }

    public Long getId() { return id; }
    public Long getChannelId() { return channelId; }
    public String getRuntimeVersion() { return runtimeVersion; }
    public String getReleaseId() { return releaseId; }
    public void setReleaseId(String releaseId) { this.releaseId = releaseId; }
    public int getRolloutPercent() { return rolloutPercent; }
    public void setRolloutPercent(int rolloutPercent) { this.rolloutPercent = rolloutPercent; }
    public String getPreviousReleaseId() { return previousReleaseId; }
    public void setPreviousReleaseId(String previousReleaseId) { this.previousReleaseId = previousReleaseId; }
    public boolean isRollbackToEmbedded() { return Boolean.TRUE.equals(rollbackToEmbedded); }
    public void setRollbackToEmbedded(boolean rollbackToEmbedded) { this.rollbackToEmbedded = rollbackToEmbedded; }
    public Instant getRollbackAt() { return rollbackAt; }
    public void setRollbackAt(Instant rollbackAt) { this.rollbackAt = rollbackAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
    public String getActivationReleaseId() { return activationReleaseId; }
    public Instant getActivationAt() { return activationAt; }
    public String getActivationBy() { return activationBy; }

    /** 设口令更新包（null 是取消，口令激活改回拿线上版本） */
    public void setActivation(String releaseId, String by) {
        this.activationReleaseId = releaseId;
        this.activationAt = Instant.now();
        this.activationBy = by;
    }
}
