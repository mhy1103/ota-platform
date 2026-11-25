package com.ota.platform.schedule;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** 计划任务：到时间自动切换 A/B 面，或者把某个渠道的线上版本调整到指定灰度 */
@Entity
@Table(name = "ota_scheduled_task", indexes = @Index(name = "idx_task_due", columnList = "status,run_at"))
public class ScheduledTask {

    public static final String MODE = "MODE";
    public static final String ROLLOUT = "ROLLOUT";
    public static final String PENDING = "PENDING", DONE = "DONE", FAILED = "FAILED", CANCELLED = "CANCELLED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(nullable = false, length = 16)
    private String kind;

    /** MODE：切到 A 还是 B */
    @Column(length = 1)
    private String face;

    /** ROLLOUT：渠道、运行版本、设定时的线上版本、要调到的灰度 */
    @Column(length = 40)
    private String channel;

    @Column(length = 64)
    private String runtime;

    @Column(name = "release_id", length = 64)
    private String releaseId;

    private Integer percent;

    @Column(name = "run_at", nullable = false)
    private LocalDateTime runAt;

    @Column(nullable = false, length = 16)
    private String status = PENDING;

    @Column(length = 300)
    private String result;

    @Column(name = "created_by", length = 64)
    private String createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "done_at")
    private LocalDateTime doneAt;

    protected ScheduledTask() {}

    public static ScheduledTask mode(Long appId, boolean b, LocalDateTime runAt, String by) {
        ScheduledTask t = new ScheduledTask();
        t.appId = appId;
        t.kind = MODE;
        t.face = b ? "B" : "A";
        t.runAt = runAt;
        t.createdBy = by;
        return t;
    }

    public static ScheduledTask rollout(Long appId, String channel, String runtime, String releaseId, int percent, LocalDateTime runAt, String by) {
        ScheduledTask t = new ScheduledTask();
        t.appId = appId;
        t.kind = ROLLOUT;
        t.channel = channel;
        t.runtime = runtime;
        t.releaseId = releaseId;
        t.percent = percent;
        t.runAt = runAt;
        t.createdBy = by;
        return t;
    }

    public void finish(String status, String result) {
        this.status = status;
        this.result = result == null ? null : result.length() > 300 ? result.substring(0, 297) + "…" : result;
        this.doneAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public String getKind() { return kind; }
    public String getFace() { return face; }
    public String getChannel() { return channel; }
    public String getRuntime() { return runtime; }
    public String getReleaseId() { return releaseId; }
    public Integer getPercent() { return percent; }
    public LocalDateTime getRunAt() { return runAt; }
    public String getStatus() { return status; }
    public String getResult() { return result; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getDoneAt() { return doneAt; }
    public boolean isPending() { return PENDING.equals(status); }

    /** 页面上显示的「做什么」 */
    public String getWhat() {
        if (MODE.equals(kind)) return "B".equals(face) ? "切换到 B 面（上线模式）" : "切换到 A 面（审核模式）";
        return channel + " 灰度调整到 " + percent + "%";
    }
}
