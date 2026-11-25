package com.ota.platform.release;

import jakarta.persistence.*;

import java.time.Instant;

/** 一次发布。id 是 UUID，同时也是 Expo 清单里的更新 ID。 */
@Entity
@Table(name = "ota_release", indexes = @Index(name = "idx_release_app_created", columnList = "app_id,created_at"))
public class Release {

    public static final String SOURCE_UPLOAD = "upload";
    public static final String SOURCE_CLI = "cli";
    public static final String SOURCE_EXTERNAL = "external";
    public static final String SOURCE_REPUBLISH = "republish";

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "runtime_version", nullable = false, length = 64)
    private String runtimeVersion;

    @Column(length = 500)
    private String message;

    /** 为 true 时 App 下载完成后立即重启（写进清单的 extra.ota.forceUpdate） */
    @Column(name = "force_update", nullable = false)
    private boolean forceUpdate;

    /** 包含的平台，逗号分隔，比如 android,ios */
    @Column(nullable = false, length = 64)
    private String platforms;

    /** MySQL 的 TEXT 最多 64 KB，资源多的 App 会超 */
    @Column(name = "metadata_json", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String metadataJson;

    @Column(name = "expo_config_json", columnDefinition = "MEDIUMTEXT")
    private String expoConfigJson;

    @Column(name = "total_bytes", nullable = false)
    private long totalBytes;

    /** upload / cli / external / republish */
    @Column(length = 16)
    private String source;

    /** 外部链接版本：dist 目录在 CDN 上的地址（以 / 结尾），App 直接从这里下载文件 */
    @Column(name = "external_base_url", length = 1000)
    private String externalBaseUrl;

    /** 重新发布（回滚）出来的版本，记下原版本 */
    @Column(name = "republished_from", length = 36)
    private String republishedFrom;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public boolean isExternal() {
        return externalBaseUrl != null && !externalBaseUrl.isBlank();
    }

    public String shortId() {
        return id == null ? "" : id.substring(0, 8);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public String getRuntimeVersion() { return runtimeVersion; }
    public void setRuntimeVersion(String runtimeVersion) { this.runtimeVersion = runtimeVersion; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public boolean isForceUpdate() { return forceUpdate; }
    public void setForceUpdate(boolean forceUpdate) { this.forceUpdate = forceUpdate; }
    public String getPlatforms() { return platforms; }
    public void setPlatforms(String platforms) { this.platforms = platforms; }
    public String getMetadataJson() { return metadataJson; }
    public void setMetadataJson(String metadataJson) { this.metadataJson = metadataJson; }
    public String getExpoConfigJson() { return expoConfigJson; }
    public void setExpoConfigJson(String expoConfigJson) { this.expoConfigJson = expoConfigJson; }
    public long getTotalBytes() { return totalBytes; }
    public void setTotalBytes(long totalBytes) { this.totalBytes = totalBytes; }
    public String getSource() { return source == null ? SOURCE_CLI : source; }
    public void setSource(String source) { this.source = source; }
    public String getExternalBaseUrl() { return externalBaseUrl; }
    public void setExternalBaseUrl(String externalBaseUrl) { this.externalBaseUrl = externalBaseUrl; }
    public String getRepublishedFrom() { return republishedFrom; }
    public void setRepublishedFrom(String republishedFrom) { this.republishedFrom = republishedFrom; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
    public Instant getCreatedAt() { return createdAt; }
}
