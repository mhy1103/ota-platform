package com.ota.platform.settings;

import jakarta.persistence.*;

/** 简单的键值配置表：管理员账号密码、主题、圆角等 */
@Entity
@Table(name = "app_setting")
public class Setting {

    @Id
    @Column(name = "setting_key", length = 64)
    private String key;

    @Column(name = "setting_value", length = 2000)
    private String value;

    public Setting() {}

    public Setting(String key, String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
}
