package com.ota.platform.admin;

import jakarta.persistence.*;

/** 成员在一个 App 上的授权：有这一行就能查看，perms 里是查看之外的权限 */
@Entity
@Table(name = "ota_admin_grant", uniqueConstraints = @UniqueConstraint(columnNames = {"admin_id", "app_id"}))
public class AdminGrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_id", nullable = false)
    private Long adminId;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(nullable = false, length = 100)
    private String perms = "";

    protected AdminGrant() {}

    public AdminGrant(Long adminId, Long appId, String perms) {
        this.adminId = adminId;
        this.appId = appId;
        this.perms = perms == null ? "" : perms;
    }

    public Long getId() { return id; }
    public Long getAdminId() { return adminId; }
    public Long getAppId() { return appId; }
    public String getPerms() { return perms; }
    public void setPerms(String perms) { this.perms = perms == null ? "" : perms; }
}
