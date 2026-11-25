package com.ota.platform.stats;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * 每个 App 每天的设备数：当天第一次检查更新时算一台活跃设备，第一次出现的算新设备。
 * 今天的数直接按设备表的最近检查时间算，这里主要留下以前每天的数（设备表只记最近一次）。
 */
@Entity
@Table(name = "ota_device_daily", uniqueConstraints = @UniqueConstraint(columnNames = {"app_id", "stat_day"}),
        indexes = @Index(name = "idx_device_daily_day", columnList = "stat_day"))
public class DeviceDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "stat_day", nullable = false)
    private LocalDate day;

    @Column(nullable = false)
    private int active;

    @Column(name = "new_devices", nullable = false)
    private int newDevices;

    @Column(nullable = false)
    private int ios;

    @Column(nullable = false)
    private int android;

    protected DeviceDaily() {}

    public DeviceDaily(Long appId, LocalDate day) {
        this.appId = appId;
        this.day = day;
    }

    public Long getId() { return id; }
    public Long getAppId() { return appId; }
    public LocalDate getDay() { return day; }
    public int getActive() { return active; }
    public int getNewDevices() { return newDevices; }
    public int getIos() { return ios; }
    public int getAndroid() { return android; }

    public void count(boolean isNew, String platform) {
        active++;
        if (isNew) newDevices++;
        if ("ios".equalsIgnoreCase(platform)) ios++;
        else if ("android".equalsIgnoreCase(platform)) android++;
    }

    /** 演示数据用：直接写一天的数 */
    public void set(int active, int newDevices, int ios, int android) {
        this.active = active;
        this.newDevices = newDevices;
        this.ios = ios;
        this.android = android;
    }
}
