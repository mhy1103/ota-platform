package com.ota.platform.app;

import jakarta.persistence.*;

import java.time.Instant;

/** 发布渠道，比如 production、staging。App 用请求头 expo-channel-name 选择，默认 production。 */
@Entity
@Table(name = "ota_channel", uniqueConstraints = @UniqueConstraint(columnNames = {"app_id", "name"}))
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(nullable = false, length = 40)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Channel() {}

    public Channel(Long appId, String name) {
        this.appId = appId;
        this.name = name;
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
