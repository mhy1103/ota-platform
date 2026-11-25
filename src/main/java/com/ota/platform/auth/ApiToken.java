package com.ota.platform.auth;

import jakarta.persistence.*;

import java.time.Instant;

/** 管理接口的访问令牌。只存 SHA-256，明文只在创建时返回一次。 */
@Entity
@Table(name = "ota_api_token")
public class ApiToken {

    public static final String SCOPE_ADMIN = "admin";
    public static final String SCOPE_PUBLISH = "publish";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** admin：管理所有 App；publish：只能给 appId 这个 App 发布 */
    @Column(nullable = false, length = 16)
    private String scope;

    @Column(name = "app_id")
    private Long appId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    /** 明文的开头几位（如 ota_AbCd），列表里区分令牌用；启动参数指定的令牌不存 */
    @Column(name = "token_prefix", length = 16)
    private String tokenPrefix;

    protected ApiToken() {}

    public ApiToken(String name, String tokenHash, String scope, Long appId) {
        this.name = name;
        this.tokenHash = tokenHash;
        this.scope = scope;
        this.appId = appId;
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getTokenHash() { return tokenHash; }
    public String getScope() { return scope; }
    public Long getAppId() { return appId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
    public void setLastUsedAt(Instant lastUsedAt) { this.lastUsedAt = lastUsedAt; }
    public String getTokenPrefix() { return tokenPrefix; }
    public void setTokenPrefix(String tokenPrefix) { this.tokenPrefix = tokenPrefix; }
}
