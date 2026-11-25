package com.ota.platform.audit;

import com.ota.platform.app.OtaApp;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** 这次请求操作的是哪个 App：后台按当前 App、接口按地址里的 App 设置，写操作日志时带上 */
public final class AppContext {

    private static final String ATTR = "ota.app.id";

    private AppContext() {}

    public static void set(OtaApp app) {
        set(app == null ? null : app.getId());
    }

    public static void set(Long appId) {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra != null) ra.setAttribute(ATTR, appId, RequestAttributes.SCOPE_REQUEST);
    }

    public static Long get() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        return ra == null ? null : (Long) ra.getAttribute(ATTR, RequestAttributes.SCOPE_REQUEST);
    }
}
