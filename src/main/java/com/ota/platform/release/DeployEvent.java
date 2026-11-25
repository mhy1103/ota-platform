package com.ota.platform.release;

import jakarta.persistence.*;

import java.time.Instant;

/** 发布记录：哪个渠道、哪个 Runtime，什么时候、谁做了什么（发布、调灰度、回滚、回退内置版本） */
@Entity
@Table(name = "ota_deploy_event", indexes = @Index(name = "idx_deploy_event_app", columnList = "app_id, at"))
public class DeployEvent {

    public static final String DEPLOY = "DEPLOY", ROLLOUT = "ROLLOUT", ROLLBACK = "ROLLBACK", EMBEDDED = "EMBEDDED",
            ACTIVATION = "ACTIVATION", ACTIVATION_OFF = "ACTIVATION_OFF";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(nullable = false, length = 64)
    private String channel;

    @Column(nullable = false, length = 64)
    private String runtime;

    @Column(nullable = false, length = 16)
    private String action;

    /** 这次上线的版本（回滚时是复制出来的新版本） */
    @Column(name = "release_id", length = 36)
    private String releaseId;

    /** 版本内容来自哪个版本：复制出来的版本记原版本 */
    @Column(name = "origin_id", length = 36)
    private String originId;

    @Column(nullable = false)
    private int rollout;

    @Column(length = 80)
    private String actor;

    @Column(nullable = false)
    private Instant at = Instant.now();

    protected DeployEvent() {}

    public DeployEvent(Long appId, String channel, String runtime, String action, Release release, int rollout, String actor) {
        this.appId = appId;
        this.channel = channel;
        this.runtime = runtime;
        this.action = action;
        if (release != null) {
            this.releaseId = release.getId();
            this.originId = ReleaseService.origin(release);
        }
        this.rollout = rollout;
        this.actor = actor == null ? null : actor.length() > 80 ? actor.substring(0, 80) : actor;
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public String getChannel() { return channel; }
    public String getRuntime() { return runtime; }
    public String getAction() { return action; }
    public String getReleaseId() { return releaseId; }
    public String getOriginId() { return originId; }
    public int getRollout() { return rollout; }
    public String getActor() { return actor; }
    public Instant getAt() { return at; }
    public boolean isCopy() { return releaseId != null && !releaseId.equals(originId); }

    public String getActionLabel() {
        return switch (action) {
            case ROLLOUT -> "调整灰度";
            case ROLLBACK -> "回滚";
            case EMBEDDED -> "回退内置版本";
            case ACTIVATION -> "设为口令更新包";
            case ACTIVATION_OFF -> "取消口令更新包";
            default -> "发布";
        };
    }
}
