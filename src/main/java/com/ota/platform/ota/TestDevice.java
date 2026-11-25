package com.ota.platform.ota;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 测试设备：这台设备检查更新时用指定渠道，不管 App 里写的是哪个渠道。新版本先给测试机，不用重新打包。
 * 勾了「灰度白名单」的，渠道灰度没到 100% 时也算在灰度里，先拿到线上版本。
 */
@Entity
@Table(name = "ota_test_device", uniqueConstraints = @UniqueConstraint(columnNames = {"app_id", "client_id"}))
public class TestDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "client_id", nullable = false, length = 100)
    private String clientId;

    @Column(length = 50)
    private String name;

    @Column(nullable = false, length = 40)
    private String channel;

    @Column(name = "rollout_first")
    private Boolean rolloutFirst;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected TestDevice() {}

    public TestDevice(Long appId, String clientId, String name, String channel) {
        this.appId = appId;
        this.clientId = clientId;
        this.name = name;
        this.channel = channel;
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public String getClientId() { return clientId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public boolean isRolloutFirst() { return Boolean.TRUE.equals(rolloutFirst); }
    public void setRolloutFirst(boolean rolloutFirst) { this.rolloutFirst = rolloutFirst; }
    public Instant getCreatedAt() { return createdAt; }
}
