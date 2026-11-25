package com.ota.platform;

import com.ota.platform.config.OtaProperties;
import com.ota.platform.install.Restarter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

// 登录账号来自系统配置和代理表（SecurityConfig.userDetailsService），不需要 Spring Boot 自动生成的默认用户
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@EnableConfigurationProperties(OtaProperties.class)
public class OtaPlatformApplication {

    public static void main(String[] args) {
        Restarter.remember(OtaPlatformApplication.class, args);
        SpringApplication.run(OtaPlatformApplication.class, args);
    }

    /** jar 里的版本号（pom 的 version），开发时没有就显示 dev */
    public static String version() {
        String v = OtaPlatformApplication.class.getPackage().getImplementationVersion();
        return v == null || v.isBlank() ? "dev" : v;
    }
}
