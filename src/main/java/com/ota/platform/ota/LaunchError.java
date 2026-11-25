package com.ota.platform.ota;

import jakarta.persistence.*;

import java.time.Instant;

/** App 上次启动崩溃的错误信息（expo-updates 在下次检查更新时用 Expo-Fatal-Error 带上来） */
@Entity
@Table(name = "ota_launch_error", indexes = {
        @Index(name = "idx_launch_error_app", columnList = "app_id,created_at"),
        @Index(name = "idx_launch_error_release", columnList = "release_id")})
public class LaunchError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    /** 出错的版本：设备报告启动失败的版本，没有就是它当时运行的版本 */
    @Column(name = "release_id", length = 64)
    private String releaseId;

    @Column(name = "client_id", length = 100)
    private String clientId;

    @Column(length = 16)
    private String platform;

    @Column(name = "runtime_version", length = 64)
    private String runtimeVersion;

    @Column(nullable = false, length = 1024)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected LaunchError() {}

    public LaunchError(Long appId, String releaseId, String clientId, String platform, String runtimeVersion, String message) {
        this.appId = appId;
        this.releaseId = releaseId;
        this.clientId = clientId;
        this.platform = platform;
        this.runtimeVersion = runtimeVersion;
        this.message = message.length() > 1024 ? message.substring(0, 1024) : message;
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public String getReleaseId() { return releaseId; }
    public String getClientId() { return clientId; }
    public String getPlatform() { return platform; }
    public String getRuntimeVersion() { return runtimeVersion; }
    public String getMessage() { return message; }
    public Instant getCreatedAt() { return createdAt; }
}
