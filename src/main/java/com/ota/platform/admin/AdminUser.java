package com.ota.platform.admin;

import jakarta.persistence.*;

import java.time.Instant;

/** 后台管理员。owner 是超级管理员，能管理所有 App 和系统；其他成员只能操作授权给他的 App。 */
@Entity
@Table(name = "ota_admin_user")
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String username;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false)
    private boolean owner;

    /** 能管理代理（新建、编辑、扣量、预览）。代理是所有 App 共用的 */
    @Column(name = "manage_agents", nullable = false)
    private boolean manageAgents;

    /** 能管理口令、导出激活记录 */
    @Column(name = "manage_codes", nullable = false)
    private boolean manageCodes;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(length = 200)
    private String remark;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** 改密码、停用、改账号的时间：在这之前登录的会话作废 */
    @Column(name = "credentials_at", nullable = false)
    private Instant credentialsAt = Instant.now();

    /** 看过的引导，逗号分隔：welcome、page:codes …… */
    @Column(length = 400)
    private String guide;

    /** 两步验证的密钥（主密钥加密保存）；为空表示没开 */
    @Column(name = "totp_secret", length = 200)
    private String totpSecret;

    /** 恢复码的 SHA-256，逗号分隔，用一个删一个 */
    @Column(name = "totp_recovery", length = 900)
    private String totpRecovery;

    @Column(name = "totp_at")
    private Instant totpAt;

    /** 超级管理员建账号、重置密码时可以要求 TA 下次登录先改密码 */
    @Column(name = "must_change_password")
    private Boolean mustChangePassword;

    @Column(length = 2000)
    private String prefs;

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public boolean isOwner() { return owner; }
    public void setOwner(boolean owner) { this.owner = owner; }
    public boolean isManageAgents() { return manageAgents; }
    public void setManageAgents(boolean manageAgents) { this.manageAgents = manageAgents; }
    public boolean isManageCodes() { return manageCodes; }
    public void setManageCodes(boolean manageCodes) { this.manageCodes = manageCodes; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public Instant getCredentialsAt() { return credentialsAt; }
    public void setCredentialsAt(Instant credentialsAt) { this.credentialsAt = credentialsAt; }
    public String getPrefs() { return prefs; }
    public void setPrefs(String prefs) { this.prefs = prefs; }
    public String getGuide() { return guide; }
    public void setGuide(String guide) { this.guide = guide; }
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
    public String getTotpRecovery() { return totpRecovery; }
    public void setTotpRecovery(String totpRecovery) { this.totpRecovery = totpRecovery; }
    public Instant getTotpAt() { return totpAt; }
    public void setTotpAt(Instant totpAt) { this.totpAt = totpAt; }
    public boolean isTotpEnabled() { return totpSecret != null && !totpSecret.isBlank(); }
    public boolean isMustChangePassword() { return Boolean.TRUE.equals(mustChangePassword); }
    public void setMustChangePassword(boolean v) { this.mustChangePassword = v; }
}
