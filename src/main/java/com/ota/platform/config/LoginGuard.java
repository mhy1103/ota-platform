package com.ota.platform.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败次数限制，防止暴力猜密码：一段时间内同一 IP 同一账号错到次数，或同一 IP 换着账号错到 4 倍（至少 20 次），暂停登录到这段时间过去。
 * 次数和时长在「系统配置 → 登录安全」里改，默认 15 分钟内错 5 次。
 * IP 取离服务最近的一层代理记下的地址，客户端自己伪造的 X-Forwarded-For 绕不过去。
 */
@Component
public class LoginGuard {

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();
    private final com.ota.platform.settings.SettingService settings;

    public LoginGuard(com.ota.platform.settings.SettingService settings) {
        this.settings = settings;
    }

    /** 同一 IP 换着账号试，错多少次暂停 */
    public static int perIp(int perAccount) {
        return Math.max(20, perAccount * 4);
    }

    public boolean locked(String ip, String username) {
        int fails = settings.lockFails();
        return count("ip:" + ip) >= perIp(fails) || count(accountKey(ip, username)) >= fails;
    }

    public void failed(String ip, String username) {
        if (failures.size() > 50_000) evictStale();
        add("ip:" + ip);
        add(accountKey(ip, username));
    }

    public void succeeded(String ip, String username) {
        failures.remove(accountKey(ip, username));
    }

    public int windowMinutes() {
        return settings.lockMinutes();
    }

    private static String accountKey(String ip, String username) {
        return "acct:" + ip + "|" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

    private int count(String key) {
        Deque<Long> q = failures.get(key);
        if (q == null) return 0;
        synchronized (q) {
            prune(q, System.currentTimeMillis());
            return q.size();
        }
    }

    private void add(String key) {
        Deque<Long> q = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            long now = System.currentTimeMillis();
            prune(q, now);
            q.addLast(now);
        }
    }

    /** 只丢窗口外的记录。全部清空会把正在被暂停的 IP 也放出来 */
    private void evictStale() {
        long now = System.currentTimeMillis();
        failures.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            synchronized (q) {
                prune(q, now);
                return q.isEmpty();
            }
        });
        if (failures.size() > 200_000) failures.clear();
    }

    private void prune(Deque<Long> q, long now) {
        long window = settings.lockMinutes() * 60_000L;
        while (!q.isEmpty() && now - q.peekFirst() > window) q.pollFirst();
    }

    /** 本机或内网连进来的（前面有反向代理）取代理追加的最后一个地址，否则取直连地址 */
    public static String ipOf(HttpServletRequest req) {
        String remote = req.getRemoteAddr();
        if (isPrivate(remote)) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) {
                String[] parts = xff.split(",");
                return parts[parts.length - 1].trim();
            }
            String real = req.getHeader("X-Real-IP");
            if (real != null && !real.isBlank()) return real.trim();
        }
        return remote;
    }

    private static boolean isPrivate(String ip) {
        if (ip == null || ip.isBlank() || !ip.matches("[0-9a-fA-F:.]+")) return false;
        try {
            InetAddress a = InetAddress.getByName(ip);
            return a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress();
        } catch (Exception e) {
            return false;
        }
    }

    /** 登录请求先过这里：已暂停的直接回登录页，不再校验密码 */
    public OncePerRequestFilter filter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                    throws ServletException, IOException {
                if ("POST".equals(req.getMethod()) && "/login".equals(req.getServletPath())
                        && locked(ipOf(req), req.getParameter("username"))) {
                    res.sendRedirect("/login?locked");
                    return;
                }
                chain.doFilter(req, res);
            }
        };
    }
}
