package com.ota.platform.demo;

import com.ota.platform.admin.AdminUser;
import com.ota.platform.admin.AdminUserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** 演示环境启动时：确保有演示账号（超级管理员），密码是正式环境这次启动给的随机串，只用来让正式环境代为登录 */
@Component
@ConditionalOnProperty(name = "ota.demo.mode", havingValue = "true")
public class DemoBootstrap implements ApplicationRunner {

    private final AdminUserRepo users;
    private final PasswordEncoder encoder;
    private final String username;
    private final String secret;

    public DemoBootstrap(AdminUserRepo users, PasswordEncoder encoder,
                         @Value("${ota.demo.admin:demo}") String username, @Value("${ota.demo.secret}") String secret) {
        this.users = users;
        this.encoder = encoder;
        this.username = username;
        this.secret = secret;
    }

    @Override
    public void run(ApplicationArguments args) {
        AdminUser u = users.findByUsernameIgnoreCase(username).orElseGet(AdminUser::new);
        u.setUsername(username);
        u.setName("演示账号");
        u.setPasswordHash(encoder.encode(secret));
        u.setOwner(true);
        u.setManageAgents(true);
        u.setManageCodes(true);
        u.setEnabled(true);
        u.setTotpSecret(null);
        u.setTotpRecovery(null);
        u.setTotpAt(null);
        users.save(u);
    }
}
