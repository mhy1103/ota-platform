package com.ota.platform.app;

import jakarta.persistence.*;

import java.time.Instant;

/** 一个接入热更新的 App。每个 App 有自己的签名密钥，私钥用主密钥加密后存储。 */
@Entity
@Table(name = "ota_app")
public class OtaApp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String slug;

    @Column(nullable = false, length = 100)
    private String name;

    /** 清单签名头里的 keyid，要和 App 里 codeSigningMetadata.keyid 一致 */
    @Column(name = "key_id", nullable = false, length = 40)
    private String keyId = "main";

    @Column(name = "private_key_enc", nullable = false, columnDefinition = "TEXT")
    private String privateKeyEnc;

    @Column(name = "certificate_pem", nullable = false, columnDefinition = "TEXT")
    private String certificatePem;

    @Column(name = "key_updated_at")
    private Instant keyUpdatedAt;

    /** 口令兑换开关：true 是 B 面（上线模式，口令可以兑换），false 是 A 面（审核模式） */
    @Column(name = "redeem_open")
    private Boolean redeemOpen = false;

    @Column(name = "switched_at")
    private Instant switchedAt;

    /** 口令接口的 App Key（公开标识）和签名密钥（加密存储） */
    @Column(name = "code_key", unique = true, length = 40)
    private String codeKey;

    @Column(name = "code_secret_enc", columnDefinition = "TEXT")
    private String codeSecretEnc;

    /** 给口令授权签名的密钥（加密存储），随备份迁移，换服务器后已签发的授权依然有效 */
    @Column(name = "grant_key_enc", columnDefinition = "TEXT")
    private String grantKeyEnc;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    @Column(name = "webhook_secret_enc", columnDefinition = "TEXT")
    private String webhookSecretEnc;

    @Column(name = "webhook_last_status", length = 300)
    private String webhookLastStatus;

    @Column(name = "webhook_last_at")
    private Instant webhookLastAt;

    /** 启动包增量更新 */
    @Column(name = "patch_enabled")
    private Boolean patchEnabled = true;

    /** 同一台手机在别的 App 已经算过有效激活，这个 App 里就不再算（按设备指纹去重）；默认关，和最早一样各算各的 */
    @Column(name = "dedupe_devices")
    private Boolean dedupeDevices = false;

    /** 开了去重时，这个 App 的设备指纹留多少天：设备这么久没打开就清掉指纹，之后重新算；0 一直留，没设过是 7 天 */
    @Column(name = "fingerprint_keep_days")
    private Integer fingerprintKeepDays = DEFAULT_FINGERPRINT_KEEP_DAYS;

    public static final int DEFAULT_FINGERPRINT_KEEP_DAYS = 7;

    /** 群机器人地址（钉钉、飞书、企业微信），发布、回滚、切换、启动失败时发消息 */
    @Column(name = "notify_url", length = 500)
    private String notifyUrl;

    /** 下载地址（App Store、应用商店或官网下载页），在 App 设置里查看和复制 */
    @Column(name = "download_url", length = 500)
    private String downloadUrl;

    @Column(name = "notify_last_status", length = 300)
    private String notifyLastStatus;

    /** 图标版本：每次上传换一个，页面用它拼图标地址（浏览器可以一直缓存）；没有图标时为空 */
    @Column(name = "icon_version", length = 16)
    private String iconVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public String getDownloadUrl() { return downloadUrl; }
    public void setDownloadUrl(String downloadUrl) { this.downloadUrl = downloadUrl; }
    public String getIconVersion() { return iconVersion; }
    public void setIconVersion(String iconVersion) { this.iconVersion = iconVersion; }
    public String getNotifyLastStatus() { return notifyLastStatus; }
    public void setNotifyLastStatus(String notifyLastStatus) { this.notifyLastStatus = notifyLastStatus; }

    public Long getId() { return id; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getKeyId() { return keyId; }
    public void setKeyId(String keyId) { this.keyId = keyId; }
    public String getPrivateKeyEnc() { return privateKeyEnc; }
    public void setPrivateKeyEnc(String privateKeyEnc) { this.privateKeyEnc = privateKeyEnc; }
    public String getCertificatePem() { return certificatePem; }
    public void setCertificatePem(String certificatePem) { this.certificatePem = certificatePem; }
    public Instant getKeyUpdatedAt() { return keyUpdatedAt != null ? keyUpdatedAt : createdAt; }
    public void setKeyUpdatedAt(Instant keyUpdatedAt) { this.keyUpdatedAt = keyUpdatedAt; }
    public boolean isRedeemOpen() { return Boolean.TRUE.equals(redeemOpen); }
    public void setRedeemOpen(boolean redeemOpen) { this.redeemOpen = redeemOpen; this.switchedAt = Instant.now(); }
    public Instant getSwitchedAt() { return switchedAt; }
    public String getCodeKey() { return codeKey; }
    public void setCodeKey(String codeKey) { this.codeKey = codeKey; }
    public String getCodeSecretEnc() { return codeSecretEnc; }
    public void setCodeSecretEnc(String codeSecretEnc) { this.codeSecretEnc = codeSecretEnc; }
    public String getGrantKeyEnc() { return grantKeyEnc; }
    public void setGrantKeyEnc(String grantKeyEnc) { this.grantKeyEnc = grantKeyEnc; }
    public String getWebhookUrl() { return webhookUrl; }
    public void setWebhookUrl(String webhookUrl) { this.webhookUrl = webhookUrl; }
    public String getWebhookSecretEnc() { return webhookSecretEnc; }
    public void setWebhookSecretEnc(String webhookSecretEnc) { this.webhookSecretEnc = webhookSecretEnc; }
    public String getWebhookLastStatus() { return webhookLastStatus; }
    public void setWebhookLastStatus(String webhookLastStatus) { this.webhookLastStatus = webhookLastStatus; }
    public Instant getWebhookLastAt() { return webhookLastAt; }
    public void setWebhookLastAt(Instant webhookLastAt) { this.webhookLastAt = webhookLastAt; }
    public boolean isPatchEnabled() { return !Boolean.FALSE.equals(patchEnabled); }
    public void setPatchEnabled(boolean patchEnabled) { this.patchEnabled = patchEnabled; }
    public boolean isDedupeDevices() { return Boolean.TRUE.equals(dedupeDevices); }
    public void setDedupeDevices(boolean dedupeDevices) { this.dedupeDevices = dedupeDevices; }
    public int getFingerprintKeepDays() { return fingerprintKeepDays == null ? DEFAULT_FINGERPRINT_KEEP_DAYS : fingerprintKeepDays; }
    public void setFingerprintKeepDays(int fingerprintKeepDays) { this.fingerprintKeepDays = Math.max(0, fingerprintKeepDays); }
    public Instant getCreatedAt() { return createdAt; }
}
