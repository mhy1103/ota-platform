package com.ota.platform.web.page;

import com.ota.platform.settings.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorViewResolver;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.ModelAndView;

import java.util.HashMap;
import java.util.Map;

/** 错误页（404、500 等）也用当前的主题色和后台名称；系统配置还读不到时（比如安装前）就用默认样式 */
@Component
public class ThemedErrorViews implements ErrorViewResolver, Ordered {

    private final ObjectProvider<SettingService> settings;

    public ThemedErrorViews(ObjectProvider<SettingService> settings) {
        this.settings = settings;
    }

    @Override
    public ModelAndView resolveErrorView(HttpServletRequest request, HttpStatus status, Map<String, Object> model) {
        Map<String, Object> m = new HashMap<>(model);
        try {
            SettingService s = settings.getIfAvailable();
            if (s != null) {
                m.put("themeCss", s.themeCss());
                m.put("brandName", s.brandName());
            }
        } catch (RuntimeException ignored) {
            // 读不到配置就用错误页自带的样式
        }
        return new ModelAndView("error", m, status);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
