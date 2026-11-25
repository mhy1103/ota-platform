package com.ota.platform.codes;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activation_record", uniqueConstraints = {
    @UniqueConstraint(name = "uk_activation_record", columnNames = {"app_id", "device_id", "code_id"})
}, indexes = {
    @Index(name = "idx_record_app_time", columnList = "app_id,activated_at"),
    @Index(name = "idx_record_code", columnList = "code_id,confirmed"),
    @Index(name = "idx_record_app_confirmed", columnList = "app_id,confirmed_at"),
    @Index(name = "idx_record_device", columnList = "device_id"),
    @Index(name = "idx_record_fp", columnList = "fingerprint,confirmed")
})
public class ActivationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id")
    private Long appId;

    /** 关联的口令 */
    @Column(name = "code_id", nullable = false)
    private Long codeId;

    /** 口令内容（冗余，方便查询） */
    @Column(name = "code_text")
    private String codeText;

    /** 设备唯一标识（App 生成的 UUID，存在本地） */
    @Column(name = "device_id", nullable = false)
    private String deviceId;

    /** 设备 IP */
    @Column(name = "device_ip")
    private String deviceIp;

    /** 是否真正切换到了 B 面（B 面启动后 confirm 接口回调） */
    @Column(name = "confirmed")
    private Boolean confirmed = false;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt = LocalDateTime.now();

    /** 兑换后多少毫秒就来确认：太快的判为刷的，这条永远不算有效激活；正常的为空 */
    @Column(name = "fast_ms")
    private Integer fastMs;

    /** 别的 App 里已经算过一次的那条激活（同一台手机），这条不算有效激活；正常的为空 */
    @Column(name = "dup_of")
    private Long dupOf;

    /** 按什么认出来的：fp 设备指纹，ip 兜底的 IP+机型，device 同一个设备号 */
    @Column(name = "dup_by", length = 8)
    private String dupBy;

    /** 设备指纹：/var/mobile 初始化时间 + 机型算出来的唯一值，跨 App、跨账号认同一台手机；读不到用 IP + 机型兜底，再不行为空 */
    @Column(length = 64)
    private String fingerprint;

    /** 这台设备最近打开 App 的日子（日活），指纹太久不活跃就清理时按它判断；只在算过的那条上更新 */
    @Column(name = "last_active")
    private java.time.LocalDate lastActive;

    // Getters & Setters
    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public void setAppId(Long appId) { this.appId = appId; }
    public Long getCodeId() { return codeId; }
    public void setCodeId(Long codeId) { this.codeId = codeId; }
    public String getCodeText() { return codeText; }
    public void setCodeText(String codeText) { this.codeText = codeText; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public String getDeviceIp() { return deviceIp; }
    public void setDeviceIp(String deviceIp) { this.deviceIp = deviceIp; }
    public LocalDateTime getActivatedAt() { return activatedAt; }
    public Boolean getConfirmed() { return confirmed; }
    public void setConfirmed(Boolean confirmed) { this.confirmed = confirmed; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(LocalDateTime confirmedAt) { this.confirmedAt = confirmedAt; }
    public Integer getFastMs() { return fastMs; }
    public Long getDupOf() { return dupOf; }
    public String getDupBy() { return dupBy; }
    public void markDup(Long of, String by) { this.dupOf = of; this.dupBy = by; }
    public String getFingerprint() { return fingerprint; }
    public java.time.LocalDate getLastActive() { return lastActive; }
    /** 代理那边只给是不是，不给毫秒数和指纹：非正常（确认太快）、重复（别的 App 算过这台手机） */
    public boolean isFast() { return fastMs != null; }
    public boolean isDup() { return dupOf != null; }
}
