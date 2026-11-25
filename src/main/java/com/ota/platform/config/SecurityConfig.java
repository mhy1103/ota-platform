package com.ota.platform.config;

import com.ota.platform.admin.AdminUser;
import com.ota.platform.admin.AdminUserRepo;
import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.audit.AuditService;
import com.ota.platform.auth.TokenAuthFilter;
import com.ota.platform.auth.TokenService;
import com.ota.platform.security.AdminLogins;
import com.ota.platform.security.AdminSessions;
import com.ota.platform.security.LoginHistory;
import com.ota.platform.security.LoginLifetime;
import com.ota.platform.security.TwoFactorLoginController;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Locale;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 管理接口：Authorization: Bearer 令牌，无会话 */
    @Bean
    @Order(1)
    public SecurityFilterChain apiChain(HttpSecurity http, TokenService tokens) throws Exception {
        http
            .securityMatcher("/api/v1/**")
            .csrf(c -> c.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(c -> c.disable())
            .httpBasic(b -> b.disable())
            .formLogin(f -> f.disable())
            .logout(l -> l.disable())
            .authorizeHttpRequests(a -> a.anyRequest().authenticated())
            .addFilterBefore(new TokenAuthFilter(tokens), UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(e -> e
                .authenticationEntryPoint((req, res, ex) -> json(res, 401, "UNAUTHORIZED", "缺少或无效的访问令牌"))
                .accessDeniedHandler((req, res, ex) -> json(res, 403, "FORBIDDEN", "没有权限")));
        return http.build();
    }

    /**
     * 页面只允许加载本站的脚本：后台脚本都打包成 /ui 下的文件，页面里没有内联脚本，被注入的脚本也跑不起来。
     * 样式要放行内联，主题色是按设置生成的 style。
     */
    static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; "
            + "font-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";

    /** 后台和代理端：账号密码登录。App 用的接口（清单、文件、口令）都是公开的，各自校验签名。 */
    @Bean
    @Order(2)
    public SecurityFilterChain webChain(HttpSecurity http, AgentRepo agentRepo, AuditService audit, LoginGuard guard,
                                        AdminUserRepo adminRepo, AdminLogins logins, LoginHistory history,
                                        AdminSessions sessions, LoginLifetime lifetime,
                                        com.ota.platform.share.ScreenShareService shares) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/agent/**").hasRole("AGENT")
                // 后台页面的脚本和样式只给登录了的人和打开着有效分享（数据大屏）的浏览器；登录页、安装页、错误页用的在 /vue/assets
                .requestMatchers(req -> req.getServletPath().startsWith("/vue/app/") && shares.assetsAllowed(req)).permitAll()
                .requestMatchers("/vue/app/**").authenticated()
                .anyRequest().permitAll()
            )
            .headers(h -> h
                .contentSecurityPolicy(c -> c.policyDirectives(CSP))
                .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.SAME_ORIGIN)))
            .addFilterBefore(guard.filter(), UsernamePasswordAuthenticationFilter.class)
            .formLogin(form -> form
                .loginPage("/login")
                .successHandler(successHandler(agentRepo, audit, guard, adminRepo, logins, lifetime))
                .failureHandler((req, res, ex) -> {
                    String username = req.getParameter("username");
                    if (ex instanceof DisabledException) {
                        history.failure(username, "账号已停用", req);
                        res.sendRedirect("/login?disabled");
                        return;
                    }
                    String ip = LoginGuard.ipOf(req);
                    guard.failed(ip, username);
                    if (guard.locked(ip, username)) {
                        history.failure(username, "错误次数过多，暂停登录", req);
                        audit.log("LOGIN_LOCKED", "账号 " + brief(username) + " 密码错误次数过多，暂停登录 "
                                + guard.windowMinutes() + " 分钟");
                        res.sendRedirect("/login?locked");
                    } else {
                        history.failure(username, "密码错误", req);
                        res.sendRedirect("/login?error");
                    }
                })
                .permitAll()
            )
            .exceptionHandling(e -> e
                // 未登录跳登录页要用相对地址：默认实现按反代转来的 Host 拼完整地址，会丢掉端口
                .authenticationEntryPoint((req, res, ex) -> {
                    String why = sessions.wasRevoked(req.getRequestedSessionId());
                    res.sendRedirect(why == null ? "/login" : "/login?" + why);
                })
                // 代理误入 /admin 等无权限页面时回到代理端，管理员回到后台（根路径是官网）
                .accessDeniedHandler((req, res, ex) -> {
                    var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
                    boolean agent = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
                    res.sendRedirect(agent ? "/agent" : "/admin");
                }))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .logoutSuccessUrl("/login?logout")
                .permitAll()
            );
        return http.build();
    }

    private static String brief(String s) {
        if (s == null) return "";
        String t = s.trim();
        return t.length() > 40 ? t.substring(0, 40) + "…" : t;
    }

    private AuthenticationSuccessHandler successHandler(AgentRepo agentRepo, AuditService audit, LoginGuard guard,
                                                        AdminUserRepo adminRepo, AdminLogins logins, LoginLifetime lifetime) {
        return (req, res, auth) -> {
            guard.succeeded(LoginGuard.ipOf(req), req.getParameter("username"));
            boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
            if (admin) {
                AdminUser u = adminRepo.findByUsernameIgnoreCase(auth.getName()).orElseThrow();
                if (u.isTotpEnabled()) {
                    TwoFactorLoginController.hold(req, u);
                    res.sendRedirect("/login/2fa");
                    return;
                }
                if (logins.mustPickDevices(req, u, false, LoginLifetime.wanted(req))) {
                    res.sendRedirect("/login/devices");
                    return;
                }
                logins.finish(req, u, false, LoginLifetime.wanted(req));
            } else {
                lifetime.apply(req, LoginLifetime.wanted(req));
                agentRepo.findByUsername(auth.getName()).ifPresent(agent -> {
                    agent.setLastLoginAt(LocalDateTime.now());
                    agentRepo.save(agent);
                    audit.log("AGENT_LOGIN", "代理 " + agent.getName() + "（" + agent.getUsername() + "）登录");
                });
            }
            res.sendRedirect(admin ? "/admin" : "/agent");
        };
    }

    /** 管理员在管理员表里（超级管理员多一个 OWNER 角色），代理在代理表里 */
    @Bean
    public UserDetailsService userDetailsService(AgentRepo agentRepo, AdminUserRepo adminUsers) {
        return username -> {
            AdminUser admin = adminUsers.findByUsernameIgnoreCase(username.trim()).orElse(null);
            if (admin != null) {
                return User.withUsername(admin.getUsername()).password(admin.getPasswordHash())
                        .roles(admin.isOwner() ? new String[]{"ADMIN", "OWNER"} : new String[]{"ADMIN"})
                        .disabled(!admin.isEnabled())
                        .build();
            }
            Agent agent = agentRepo.findByUsername(username.trim().toLowerCase(Locale.ROOT))
                    .orElseThrow(() -> new UsernameNotFoundException(username));
            Agent parent = agent.isSub() ? agentRepo.findById(agent.getParentId()).orElse(null) : null;
            return User.withUsername(agent.getUsername())
                    .password(agent.getPasswordHash())
                    .roles("AGENT")
                    .disabled(!com.ota.platform.agent.SubAgentService.active(agent, parent))
                    .build();
        };
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    private static void json(HttpServletResponse res, int status, String code, String message) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"error\":\"" + code + "\",\"message\":\"" + message + "\"}");
    }
}
