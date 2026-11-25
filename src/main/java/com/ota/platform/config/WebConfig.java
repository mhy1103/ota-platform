package com.ota.platform.config;

import com.ota.platform.admin.AccessInterceptor;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AccessInterceptor access;
    private final com.ota.platform.web.admin.TodoService todos;

    public WebConfig(@Lazy AccessInterceptor access, @Lazy com.ota.platform.web.admin.TodoService todos) {
        this.access = access;
        this.todos = todos;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(access).addPathPatterns("/admin", "/admin/**");
        registry.addInterceptor(new com.ota.platform.web.page.RedirectHash()).addPathPatterns("/admin/**", "/agent/**");
        // 提交了东西（后台表单、代理申请、管理接口），待处理马上重算
        registry.addInterceptor(new org.springframework.web.servlet.HandlerInterceptor() {
            @Override
            public void afterCompletion(jakarta.servlet.http.HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res, Object handler, Exception ex) {
                if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) todos.changed();
            }
        }).addPathPatterns("/admin/**", "/agent/**", "/api/**", "/v1/**");
    }

    /**
     * 强制 Tomcat 发相对重定向（Location: /admin 而不是 http://host/admin）。
     * 在反代后面时，浏览器会根据当前页面 URL 自动补全 scheme、host、port，
     * 不需要服务端知道真实的外部地址。
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> relativeRedirects() {
        return factory -> factory.addContextCustomizers(
                context -> context.setUseRelativeRedirects(true));
    }
}
