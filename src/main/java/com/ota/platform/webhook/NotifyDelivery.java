package com.ota.platform.webhook;

import jakarta.persistence.*;
import java.time.Instant;

/** 一次通知的发送结果：Webhook（重试完的最终结果）或群机器人。只记对方的域名，地址里的令牌不落库 */
@Entity
@Table(name = "ota_notify_delivery", indexes = @Index(name = "idx_notify_delivery_app", columnList = "app_id, id"))
public class NotifyDelivery {

    public static final String WEBHOOK = "WEBHOOK";
    public static final String BOT = "BOT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(nullable = false, length = 16)
    private String channel;

    /** Webhook 是事件名，群机器人是消息开头 */
    @Column(nullable = false, length = 80)
    private String event;

    @Column(length = 120)
    private String target;

    @Column(nullable = false)
    private boolean ok;

    @Column(length = 300)
    private String result;

    @Column(nullable = false)
    private int attempts = 1;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected NotifyDelivery() {}

    public NotifyDelivery(Long appId, String channel, String event, String target, boolean ok, String result, int attempts) {
        this.appId = appId;
        this.channel = channel;
        this.event = event;
        this.target = target;
        this.ok = ok;
        this.result = result;
        this.attempts = attempts;
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public String getChannel() { return channel; }
    public String getEvent() { return event; }
    public String getTarget() { return target; }
    public boolean isOk() { return ok; }
    public String getResult() { return result; }
    public int getAttempts() { return attempts; }
    public Instant getCreatedAt() { return createdAt; }
}
