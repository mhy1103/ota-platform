package com.ota.platform.share;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 分享出去的数据大屏：拿到链接（/s/口令）不用登录就能看，和分享的人在后台看到的一样，只是不能操作。
 * 看哪个范围、按谁的权限取数都在这里；可以设有效期和访问密码，随时取消
 */
@Entity
@Table(name = "screen_share", uniqueConstraints = @UniqueConstraint(name = "uk_screen_share_token", columnNames = "token"),
        indexes = @Index(name = "idx_screen_share_created", columnList = "created_at"))
public class ScreenShare {

    public static final String APP = "app";
    public static final String ALL = "all";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 链接里的那一段，随机生成 */
    @Column(nullable = false, length = 40)
    private String token;

    /** app：一个 App；all：分享人能看到的全部 App */
    @Column(nullable = false, length = 8)
    private String scope = APP;

    @Column(name = "app_id")
    private Long appId;

    /** 分享人的登录账号：按 TA 现在的权限取数，TA 停用、删了分享就失效 */
    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

    @Column(name = "created_name", length = 64)
    private String createdName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** null 是长期有效 */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /** 访问密码（BCrypt），null 是不要密码 */
    @Column(name = "pass_hash", length = 100)
    private String passHash;

    /** 浏览器记住「密码输对了」用的签名里带着它 */
    @Column(nullable = false, length = 24)
    private String stamp;

    @Column(length = 60)
    private String note;

    /** 打开时默认的深浅色，跟着分享时分享人的大屏 */
    @Column(length = 8)
    private String theme;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by", length = 64)
    private String revokedBy;

    /** 打开过几次（同一个浏览器开着不算新的一次） */
    private long views;

    @Column(name = "last_viewed_at")
    private LocalDateTime lastViewedAt;

    protected ScreenShare() {}

    public ScreenShare(String token, String scope, Long appId, String createdBy, String createdName, LocalDateTime expiresAt,
                       String passHash, String stamp, String note, String theme) {
        this.token = token;
        this.scope = ALL.equals(scope) ? ALL : APP;
        this.appId = appId;
        this.createdBy = createdBy;
        this.createdName = createdName;
        this.expiresAt = expiresAt;
        this.passHash = passHash;
        this.stamp = stamp;
        this.note = note;
        this.theme = theme;
    }

    public Long getId() { return id; }
    public String getToken() { return token; }
    public String getScope() { return scope; }
    public boolean isAll() { return ALL.equals(scope); }
    public Long getAppId() { return appId; }
    public String getCreatedBy() { return createdBy; }
    public String getCreatedName() { return createdName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public String getPassHash() { return passHash; }
    public boolean isLocked() { return passHash != null && !passHash.isEmpty(); }
    public String getStamp() { return stamp; }
    public String getNote() { return note; }
    public String getTheme() { return theme; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public String getRevokedBy() { return revokedBy; }
    public long getViews() { return views; }
    public LocalDateTime getLastViewedAt() { return lastViewedAt; }

    public void revoke(String by) {
        this.revokedAt = LocalDateTime.now();
        this.revokedBy = by;
    }

    public void viewed(boolean fresh, LocalDateTime at) {
        if (fresh) views++;
        lastViewedAt = at;
    }

    public boolean expired(LocalDateTime now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }
}
