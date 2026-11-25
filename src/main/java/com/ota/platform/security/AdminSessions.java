package com.ota.platform.security;

import jakarta.servlet.http.HttpSession;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理员当前登录着的会话（登录设备）。会话存在数据库里，重启服务也还在；
 * 「退出其他设备」直接删掉那些会话，对方下一次操作就回到登录页。
 */
@Component
public class AdminSessions {

    static final String ADMIN = "ota.admin", DEVICE = "ota.device", MOBILE = "ota.mobile", KEY = "ota.key";
    private static final String SIGNED_OUT = "revoked", GONE = "expired";

    public record Info(String key, String device, boolean mobile, Instant since, Instant seen, boolean current) {}

    /** 刚被退出的会话和原因：对方下次打开页面时告诉他为什么回到了登录页，一天后忘掉 */
    private record Revoked(long at, String reason) {}

    private final FindByIndexNameSessionRepository<? extends Session> repo;
    private final Map<String, Revoked> revoked = new ConcurrentHashMap<>();

    public AdminSessions(FindByIndexNameSessionRepository<? extends Session> repo) {
        this.repo = repo;
    }

    /** 登录完成时记下是谁、用什么设备；页面上用随机的 key 指代会话，不把真正的会话号写进页面 */
    public void register(HttpSession session, Long adminId, String username, String userAgent) {
        Devices.Device d = Devices.of(userAgent);
        session.setAttribute(FindByIndexNameSessionRepository.PRINCIPAL_NAME_INDEX_NAME, username);
        session.setAttribute(ADMIN, adminId);
        session.setAttribute(DEVICE, d.label());
        session.setAttribute(MOBILE, d.mobile());
        session.setAttribute(KEY, UUID.randomUUID().toString().replace("-", "").substring(0, 20));
    }

    public List<Info> of(Long adminId, String username, String currentId) {
        return mine(adminId, username).entrySet().stream()
                .map(e -> {
                    Session s = e.getValue();
                    String device = s.getAttribute(DEVICE);
                    return new Info(s.getAttribute(KEY), device == null ? "未知设备" : device, Boolean.TRUE.equals(s.getAttribute(MOBILE)),
                            s.getCreationTime(), s.getLastAccessedTime(), e.getKey().equals(currentId));
                })
                .sorted(Comparator.comparing(Info::current).reversed().thenComparing(Info::seen, Comparator.reverseOrder()))
                .toList();
    }

    /** 这个管理员现在登录着几台设备 */
    public int count(Long adminId, String username) {
        return mine(adminId, username).size();
    }

    /** 登录设备满了，登录时选的那几台：按指代退出，返回退出了几个 */
    public int revokeKeys(Long adminId, String username, java.util.Collection<String> keys) {
        int n = 0;
        for (Map.Entry<String, ? extends Session> e : mine(adminId, username).entrySet()) {
            Object key = e.getValue().getAttribute(KEY);
            if (key != null && keys.contains(key.toString())) {
                delete(e.getKey(), SIGNED_OUT);
                n++;
            }
        }
        return n;
    }

    /** 按页面上的指代退出这个管理员的某个会话；不能用这个退出自己当前的会话 */
    public boolean revoke(Long adminId, String username, String key, String currentId) {
        if (key == null || key.isBlank()) return false;
        for (Map.Entry<String, ? extends Session> e : mine(adminId, username).entrySet()) {
            if (key.equals(e.getValue().getAttribute(KEY)) && !e.getKey().equals(currentId)) {
                delete(e.getKey(), SIGNED_OUT);
                return true;
            }
        }
        return false;
    }

    /** 退出这个管理员除了当前会话以外的所有会话，返回退出了几个 */
    public int revokeOthers(Long adminId, String username, String currentId) {
        int n = 0;
        for (String id : mine(adminId, username).keySet()) {
            if (!id.equals(currentId)) {
                delete(id, SIGNED_OUT);
                n++;
            }
        }
        return n;
    }

    /** 代理改了密码：按账号名退出他除了当前以外的会话（代理的会话不带管理员编号），返回退出了几个 */
    public int revokeOthersByName(String username, String currentId) {
        int n = 0;
        for (Map.Entry<String, ? extends Session> e : repo.findByPrincipalName(username).entrySet()) {
            if (e.getKey().equals(currentId) || e.getValue().isExpired() || e.getValue().getAttribute(ADMIN) != null) continue;
            delete(e.getKey(), SIGNED_OUT);
            n++;
        }
        return n;
    }

    /** 管理员被删除时，把他所有会话都退出 */
    public void revokeAll(Long adminId, String username) {
        for (String id : mine(adminId, username).keySet()) delete(id, GONE);
    }

    /** 这个会话是被退出的吗：返回登录页要带的原因（revoked / expired），不是返回 null */
    public String wasRevoked(String id) {
        if (id == null) return null;
        Revoked r = revoked.remove(id);
        return r != null && System.currentTimeMillis() - r.at() < 86_400_000L ? r.reason() : null;
    }

    private Map<String, ? extends Session> mine(Long adminId, String username) {
        Map<String, ? extends Session> found = repo.findByPrincipalName(username);
        found.values().removeIf(s -> s.isExpired() || !adminId.equals(s.getAttribute(ADMIN)));
        return found;
    }

    private void delete(String id, String reason) {
        long now = System.currentTimeMillis();
        if (revoked.size() > 5000) revoked.values().removeIf(r -> now - r.at() > 86_400_000L);
        revoked.put(id, new Revoked(now, reason));
        repo.deleteById(id);
    }
}
