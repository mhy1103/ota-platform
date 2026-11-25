package com.ota.platform.security;

import java.util.Locale;

/** 从 User-Agent 里认出浏览器和系统，显示成「Chrome · macOS」这样 */
public final class Devices {

    private Devices() {}

    public record Device(String label, boolean mobile) {}

    public static Device of(String ua) {
        if (ua == null || ua.isBlank()) return new Device("未知设备", false);
        String u = ua.toLowerCase(Locale.ROOT);
        String os;
        boolean mobile = false;
        if (u.contains("iphone")) { os = "iPhone"; mobile = true; }
        else if (u.contains("ipad")) { os = "iPad"; mobile = true; }
        else if (u.contains("android")) { os = "Android"; mobile = true; }
        else if (u.contains("harmonyos") || u.contains("openharmony")) { os = "鸿蒙"; mobile = true; }
        else if (u.contains("windows")) os = "Windows";
        else if (u.contains("mac os") || u.contains("macintosh")) os = "macOS";
        else if (u.contains("cros")) os = "ChromeOS";
        else if (u.contains("linux")) os = "Linux";
        else os = null;

        String browser;
        if (u.contains("micromessenger")) browser = "微信";
        else if (u.contains("dingtalk")) browser = "钉钉";
        else if (u.contains("lark") || u.contains("feishu")) browser = "飞书";
        else if (u.contains("edg/") || u.contains("edga/") || u.contains("edgios/")) browser = "Edge";
        else if (u.contains("opr/") || u.contains("opera")) browser = "Opera";
        else if (u.contains("firefox/") || u.contains("fxios/")) browser = "Firefox";
        else if (u.contains("ucbrowser")) browser = "UC 浏览器";
        else if (u.contains("quark")) browser = "夸克";
        else if (u.contains("qqbrowser")) browser = "QQ 浏览器";
        else if (u.contains("chrome/") || u.contains("crios/")) browser = "Chrome";
        else if (u.contains("safari/")) browser = "Safari";
        else if (u.startsWith("curl/") || u.contains("python") || u.contains("java/") || u.contains("okhttp")) browser = "脚本或工具";
        else browser = "浏览器";

        String label = os == null ? browser : browser + " · " + os;
        return new Device(label.length() > 80 ? label.substring(0, 80) : label, mobile);
    }
}
