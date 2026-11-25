package com.ota.platform.ota;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 版本的更新漏斗和流量：推送给多少台设备、多少台装上、多少台启动失败，启动包完整下载和增量下载各多少次。
 * 计数在内存里攒一会儿再写库（见 ReleaseStatsService）。
 */
@Entity
@Table(name = "ota_release_stat")
public class ReleaseStat {

    @Id
    @Column(name = "release_id", length = 36)
    private String releaseId;

    /** 服务器把这个版本发给了多少台还没装上它的设备（同一台设备只算一次） */
    @Column(nullable = false)
    private long offered;

    /** 多少台设备报告正在运行这个版本 */
    @Column(nullable = false)
    private long installed;

    /** 多少台设备报告这个版本启动失败（expo-updates 会自动退回之前的版本） */
    @Column(nullable = false)
    private long failed;

    @Column(name = "patch_downloads", nullable = false)
    private long patchDownloads;

    @Column(name = "full_downloads", nullable = false)
    private long fullDownloads;

    /** 对象存储时启动包由 CDN 下载，只知道次数 */
    @Column(name = "cdn_downloads", nullable = false)
    private long cdnDownloads;

    @Column(name = "bytes_served", nullable = false)
    private long bytesServed;

    /** 增量下载比完整下载少传的字节 */
    @Column(name = "bytes_saved", nullable = false)
    private long bytesSaved;

    /** 发过启动失败告警的时间，同一个版本只告警一次 */
    @Column(name = "alerted_at")
    private Instant alertedAt;

    /** 启动失败太多被自动回滚的时间，同一个版本只自动回滚一次（回滚后又手动发布的不再自动回滚） */
    @Column(name = "rolled_back_at")
    private Instant rolledBackAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ReleaseStat() {}

    public ReleaseStat(String releaseId) {
        this.releaseId = releaseId;
    }

    public void add(long offered, long installed, long failed, long patch, long full, long cdn, long served, long saved) {
        this.offered += offered;
        this.installed += installed;
        this.failed += failed;
        this.patchDownloads += patch;
        this.fullDownloads += full;
        this.cdnDownloads += cdn;
        this.bytesServed += served;
        this.bytesSaved += saved;
        this.updatedAt = Instant.now();
    }

    /** 安装率：装上的设备 / 推送过的设备，没推送过时返回 -1 */
    public int getInstallRate() {
        return offered <= 0 ? -1 : com.ota.platform.common.Percent.of(installed, offered);
    }

    /** 启动失败率（相对推送数） */
    public int getFailRate() {
        long base = Math.max(offered, installed + failed);
        return base <= 0 ? 0 : (int) Math.min(100, Math.round(failed * 100.0 / base));
    }

    public long getDownloads() {
        return patchDownloads + fullDownloads + cdnDownloads;
    }

    public String getReleaseId() { return releaseId; }
    public long getOffered() { return offered; }
    public long getInstalled() { return installed; }
    public long getFailed() { return failed; }
    public long getPatchDownloads() { return patchDownloads; }
    public long getFullDownloads() { return fullDownloads; }
    public long getCdnDownloads() { return cdnDownloads; }
    public long getBytesServed() { return bytesServed; }
    public long getBytesSaved() { return bytesSaved; }
    public Instant getAlertedAt() { return alertedAt; }
    public void setAlertedAt(Instant alertedAt) { this.alertedAt = alertedAt; }
    public Instant getRolledBackAt() { return rolledBackAt; }
    public void setRolledBackAt(Instant rolledBackAt) { this.rolledBackAt = rolledBackAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
