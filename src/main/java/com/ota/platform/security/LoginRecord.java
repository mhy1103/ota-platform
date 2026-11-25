package com.ota.platform.security;

import jakarta.persistence.*;

import java.time.Instant;

/** 管理员登录记录：什么时候、用什么设备、成功还是失败。按要求不记 IP。 */
@Entity
@Table(name = "ota_login_record", indexes = @Index(name = "idx_login_admin_at", columnList = "admin_id, at"))
public class LoginRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id")
    private Long adminId;

    @Column(nullable = false, length = 40)
    private String username;

    @Column(nullable = false)
    private Instant at = Instant.now();

    @Column(nullable = false)
    private boolean success;

    @Column(length = 40)
    private String reason;

    @Column(length = 80)
    private String device;

    @Column(nullable = false)
    private boolean mobile;

    public Long getId() { return id; }
    public Long getAdminId() { return adminId; }
    public void setAdminId(Long adminId) { this.adminId = adminId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public Instant getAt() { return at; }
    public void setAt(Instant at) { this.at = at; }
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }
    public boolean isMobile() { return mobile; }
    public void setMobile(boolean mobile) { this.mobile = mobile; }
}
