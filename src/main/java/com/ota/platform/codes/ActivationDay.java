package com.ota.platform.codes;

import jakarta.persistence.*;

import java.time.LocalDate;

/**
 * 激活过的设备哪天打开过 App：App 启动时校验授权、确认激活就记一行（一条激活一天一行）。
 * 按口令、代理算日活和新激活的留存；act_day 是这台设备激活的那天，age 是激活后第几天（激活当天是 0），算留存不用再查激活记录
 */
@Entity
@Table(name = "activation_day", uniqueConstraints = @UniqueConstraint(name = "uk_activation_day", columnNames = {"record_id", "stat_day"}),
        indexes = {@Index(name = "idx_activation_day_code", columnList = "code_id,stat_day"),
                @Index(name = "idx_activation_day_day", columnList = "stat_day")})
public class ActivationDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "app_id", nullable = false)
    private Long appId;

    @Column(name = "code_id", nullable = false)
    private Long codeId;

    @Column(name = "stat_day", nullable = false)
    private LocalDate day;

    @Column(name = "act_day", nullable = false)
    private LocalDate actDay;

    @Column(nullable = false)
    private int age;

    protected ActivationDay() {}

    public ActivationDay(Long recordId, Long appId, Long codeId, LocalDate day, LocalDate actDay, int age) {
        this.recordId = recordId;
        this.appId = appId;
        this.codeId = codeId;
        this.day = day;
        this.actDay = actDay;
        this.age = age;
    }

    public Long getId() { return id; }
    public Long getRecordId() { return recordId; }
    public Long getAppId() { return appId; }
    public Long getCodeId() { return codeId; }
    public LocalDate getDay() { return day; }
    public LocalDate getActDay() { return actDay; }
    public int getAge() { return age; }
}
