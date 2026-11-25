package com.ota.platform.admin;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/** 成员在某个 App 上的权限。有授权就能查看这个 App，下面这些是查看之外能做的事。口令所有 App 通用，权限单独设（AdminUser.manageCodes）。 */
public enum AppPerm {
    RELEASE("发布版本", "上传、发布、灰度、回滚、删除版本，管理发布令牌"),
    MODE("切换 A/B 面", "切换运行模式（审核 / 上线）"),
    SETTINGS("App 设置", "渠道、测试设备、Webhook、群机器人、口令接口密钥、增量更新"),
    SIGNING("签名与备份", "更换签名密钥、下载和恢复备份");

    private final String label;
    private final String desc;

    AppPerm(String label, String desc) {
        this.label = label;
        this.desc = desc;
    }

    public String getKey() { return name().toLowerCase(Locale.ROOT); }
    public String getLabel() { return label; }
    public String getDesc() { return desc; }

    public static AppPerm of(String key) {
        if (key == null) return null;
        for (AppPerm p : values()) if (p.getKey().equals(key.trim().toLowerCase(Locale.ROOT))) return p;
        return null;
    }

    public static Set<AppPerm> parse(String csv) {
        Set<AppPerm> out = EnumSet.noneOf(AppPerm.class);
        if (csv == null) return out;
        for (String s : csv.split(",")) {
            AppPerm p = of(s);
            if (p != null) out.add(p);
        }
        return out;
    }

    public static String join(Set<AppPerm> perms) {
        StringBuilder sb = new StringBuilder();
        for (AppPerm p : values()) {
            if (!perms.contains(p)) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(p.getKey());
        }
        return sb.toString();
    }
}
