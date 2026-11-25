package com.ota.platform.web.page;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.RequestContextUtils;

/**
 * 提交表单后跳回页面的某个标签（redirect:/admin/app#webhook）：页面脚本用 fetch 提交、跟随跳转，拿到的地址里没有 #。
 * 把 # 后面的部分存进 flash，下一页的页面数据带回去（PageData 的 ctx.hash），浏览器里再补到地址栏上
 */
public class RedirectHash implements HandlerInterceptor {

    static final String KEY = "ota.hash";

    @Override
    public void postHandle(HttpServletRequest req, HttpServletResponse res, Object handler, ModelAndView mv) {
        if (mv == null || mv.getViewName() == null || !mv.getViewName().startsWith("redirect:")) return;
        String target = mv.getViewName();
        int i = target.indexOf('#');
        if (i < 0 || i == target.length() - 1) return;
        RequestContextUtils.getOutputFlashMap(req).put(KEY, target.substring(i));
    }
}
