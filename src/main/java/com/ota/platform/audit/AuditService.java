package com.ota.platform.audit;

import com.ota.platform.auth.TokenPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.List;

/** 操作日志：后台操作、发布、设备事件都记在 operation_log 里，带上操作人和 App。不记录 IP。 */
@Service
public class AuditService {

    /** 设备侧产生的日志，其余都是后台操作。 */
    public static final List<String> DEVICE_EVENTS = List.of("CODE_SUBMIT", "CONFIRMED", "CONFIRM_FAST", "CODE_DUP");

    /** 日志页按类别筛选 */
    public record Area(String key, String label, List<String> actions) {}

    public static final List<Area> AREAS = List.of(
            new Area("release", "版本与发布", List.of("UPLOAD", "ADD_URL", "ACTIVATE", "ROLLBACK", "DELETE", "VERIFY", "RELEASE_UPDATE",
                    "INSTALLER_UPLOAD", "INSTALLER_DELETE", "RELEASE_GUARD", "ACTIVATION_PACKAGE")),
            new Area("mode", "运行模式与计划", List.of("SWITCH", "SCHEDULE")),
            new Area("codes", "口令与激活", List.of("CODE_CREATE", "CODE_UPDATE", "CODE_TOGGLE", "CODE_DELETE", "CODE_EXPORT", "RECORD_EXPORT",
                    "CODE_SUBMIT", "CONFIRMED", "CONFIRM_FAST", "CODE_DUP")),
            new Area("agents", "代理", List.of("AGENT_CREATE", "AGENT_UPDATE", "AGENT_TOGGLE", "AGENT_DELETE", "AGENT_REFRESH", "AGENT_LOGIN",
                    "AGENT_PASSWORD", "AGENT_EXPORT", "AGENTS_EXPORT", "AGENT_RECORDS_EXPORT", "CODE_REQUEST", "CODE_REQUEST_APPROVE",
                    "CODE_REQUEST_REJECT", "CODE_REQUEST_CANCEL", "NOTICE_CREATE", "NOTICE_UPDATE", "NOTICE_DELETE",
                    "SUB_AGENT_CREATE", "SUB_AGENT_UPDATE", "SUB_AGENT_TOGGLE", "SUB_AGENT_DELETE", "SUB_AGENT_CODES", "SUB_AGENT_REFRESH",
                    "SUB_NOTICE_CREATE", "SUB_NOTICE_UPDATE", "SUB_NOTICE_DELETE")),
            new Area("app", "App 设置", List.of("APP_CREATE", "APP_UPDATE", "APP_DELETE", "APP_BACKUP", "APP_RESTORE", "CHANNEL_CREATE",
                    "CHANNEL_DELETE", "TOKEN_CREATE", "TOKEN_REVOKE", "WEBHOOK_UPDATE", "NOTIFY_UPDATE", "TESTER_SAVE", "TESTER_DELETE",
                    "SIGNING_UPDATE", "DEVICE_EXPORT")),
            new Area("account", "账号与登录", List.of("ADMIN_LOGIN", "ADMIN_CREATE", "ADMIN_UPDATE", "ADMIN_DELETE", "ACCOUNT_UPDATE",
                    "LOGIN_LOCKED", "LOGIN_2FA_LOCKED", "LOGIN_RECOVERY", "TOTP_ENABLE", "TOTP_DISABLE", "TOTP_RECOVERY", "TOTP_RESET",
                    "SESSION_REVOKE")),
            new Area("system", "系统", List.of("SETTINGS_APPEARANCE", "SETTINGS_ACCOUNT", "SECURITY_SETTINGS", "DOMAINS_UPDATE",
                    "STORAGE_SWITCH", "STORAGE_CLEAN", "INSTALL_REOPEN", "LOG_CLEAR", "LOG_EXPORT", "PROJECT_BACKUP", "PROJECT_RESTORE",
                    "DEMO_RESET", "SETTINGS_DEMO", "METRICS_TOKEN", "SCREEN_SHARE_CREATE", "SCREEN_SHARE_REVOKE")));

    public static Area area(String key) {
        return AREAS.stream().filter(a -> a.key().equals(key)).findFirst().orElse(null);
    }

    private final OperationLogRepo repo;

    public AuditService(OperationLogRepo repo) {
        this.repo = repo;
    }

    /** App 取这次请求操作的 App（见 AppContext） */
    public void log(String action, String detail) {
        log(AppContext.get(), action, detail);
    }

    public void log(Long appId, String action, String detail) {
        logAs(operator(), appId, action, detail);
    }

    /** 后台任务里没有登录信息，操作人由调用方带过来 */
    public void logAs(String operator, Long appId, String action, String detail) {
        String d = detail == null ? "" : detail;
        repo.save(new OperationLog(action, d.length() > 500 ? d.substring(0, 497) + "…" : d, cut(operator), appId));
    }

    /** 当前登录的管理员、代理或接口令牌 */
    public static String operator() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) return null;
        if (a.getPrincipal() instanceof TokenPrincipal t) return cut("令牌：" + t.name());
        return cut(a.getName());
    }

    private static String cut(String s) {
        return s == null || s.length() <= 64 ? s : s.substring(0, 64);
    }

    public static String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) return ip.split(",")[0].trim();
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) return ip.trim();
        return req.getRemoteAddr();
    }

    /** 本机、内网、链路本地地址（反向代理没传真实 IP 时大家都是这种）；不是 IP 写法的也算，免得去查域名 */
    public static boolean privateIp(String ip) {
        if (ip == null || ip.isBlank() || !ip.matches("[0-9a-fA-F:.]+")) return true;
        try {
            InetAddress a = InetAddress.getByName(ip);
            return a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress() || a.isAnyLocalAddress()
                    || (a instanceof Inet6Address && (a.getAddress()[0] & 0xfe) == 0xfc);
        } catch (Exception e) {
            return true;
        }
    }
}
