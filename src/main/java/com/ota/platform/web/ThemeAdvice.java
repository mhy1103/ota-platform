package com.ota.platform.web;

import com.ota.platform.settings.SettingService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Map;

/**
 * 所有页面都带上主题的 CSS 变量（主题色、侧栏、圆角）和后台名称。
 * 成员自己选过外观就用自己的；超级管理员选的是默认外观，代理后台、登录页和没选过的成员都用它
 */
@ControllerAdvice(basePackages = "com.ota.platform.web")
public class ThemeAdvice {

    private final SettingService settings;
    private final Prefs prefs;
    private final boolean demo;

    public ThemeAdvice(SettingService settings, Prefs prefs, @org.springframework.beans.factory.annotation.Value("${ota.demo.mode:false}") boolean demo) {
        this.settings = settings;
        this.prefs = prefs;
        this.demo = demo;
    }

    /** 演示环境：顶栏显示「演示环境」，提醒这里的操作不影响正式数据 */
    @ModelAttribute("demoMode")
    public boolean demoMode() {
        return demo;
    }

    @ModelAttribute("themeCss")
    public String themeCss() {
        Map<String, String> mine = prefs.memberLook(SecurityContextHolder.getContext().getAuthentication());
        return mine == null ? settings.themeCss() : settings.themeCss(settings.appearance(mine));
    }

    @ModelAttribute("brandName")
    public String brandName() {
        return settings.brandName();
    }
}
