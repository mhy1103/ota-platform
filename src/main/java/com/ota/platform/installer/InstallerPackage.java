package com.ota.platform.installer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/** 上传的安装包（APK / IPA，可选）：文件存在文件存储里，这里记下从包里读出来的 expo-updates 配置 */
@Entity
@Table(name = "ota_installer", indexes = @Index(name = "idx_installer_app", columnList = "app_id"))
public class InstallerPackage {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    /** ios / android */
    @Column(length = 10, nullable = false)
    private String platform;

    @Column(length = 255)
    private String fileName;

    private long size;

    @Column(length = 64, nullable = false)
    private String sha256;

    /** iOS 的 Bundle ID、Android 的包名 */
    @Column(length = 255)
    private String bundleId;

    @Column(length = 100)
    private String versionName;

    @Column(length = 50)
    private String buildNumber;

    @Column(length = 100)
    private String runtimeVersion;

    @Column(length = 500)
    private String updateUrl;

    /** 请求头里的 expo-channel-name */
    @Column(length = 100)
    private String channel;

    /** 内置签名证书（PEM），没开签名校验时为空 */
    @Column(length = 8000)
    private String certificatePem;

    /** 内置签名证书的 keyid */
    @Column(length = 100)
    private String keyId;

    @Column(length = 100)
    private String embeddedUpdateId;

    @Column(length = 30)
    private String sdkVersion;

    private Boolean updatesEnabled;

    @Column(length = 500)
    private String note;

    /** 读包时没读到、读不懂的地方，换行分隔 */
    @Column(length = 2000)
    private String warnings;

    /** 检查时机、内置包时间、ATS 等额外配置 */
    @Column(name = "extra_json", columnDefinition = "TEXT")
    private String extraJson;

    @Column(length = 64)
    private String uploadedBy;

    private Instant uploadedAt = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public long getSize() { return size; }
    public void setSize(long size) { this.size = size; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getBundleId() { return bundleId; }
    public void setBundleId(String bundleId) { this.bundleId = bundleId; }
    public String getVersionName() { return versionName; }
    public void setVersionName(String versionName) { this.versionName = versionName; }
    public String getBuildNumber() { return buildNumber; }
    public void setBuildNumber(String buildNumber) { this.buildNumber = buildNumber; }
    public String getRuntimeVersion() { return runtimeVersion; }
    public void setRuntimeVersion(String runtimeVersion) { this.runtimeVersion = runtimeVersion; }
    public String getUpdateUrl() { return updateUrl; }
    public void setUpdateUrl(String updateUrl) { this.updateUrl = updateUrl; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getCertificatePem() { return certificatePem; }
    public void setCertificatePem(String certificatePem) { this.certificatePem = certificatePem; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getEmbeddedUpdateId() { return embeddedUpdateId; }
    public void setEmbeddedUpdateId(String embeddedUpdateId) { this.embeddedUpdateId = embeddedUpdateId; }
    public String getSdkVersion() { return sdkVersion; }
    public void setSdkVersion(String sdkVersion) { this.sdkVersion = sdkVersion; }
    public Boolean getUpdatesEnabled() { return updatesEnabled; }
    public void setUpdatesEnabled(Boolean updatesEnabled) { this.updatesEnabled = updatesEnabled; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getWarnings() { return warnings; }
    public void setWarnings(String warnings) { this.warnings = warnings; }
    public String getExtraJson() { return extraJson; }
    public void setExtraJson(String extraJson) { this.extraJson = extraJson; }
    public String getUploadedBy() { return uploadedBy; }
    public void setUploadedBy(String uploadedBy) { this.uploadedBy = uploadedBy; }
    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
}
