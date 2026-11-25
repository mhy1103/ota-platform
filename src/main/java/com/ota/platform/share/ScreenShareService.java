package com.ota.platform.share;

import com.ota.platform.common.ApiException;
import com.ota.platform.settings.SettingService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 数据大屏的分享链接：生成、列出、取消；打开时校验有效期、访问密码。
 * 密码输对以后浏览器存一个签过名的 Cookie（这个分享、到什么时候），下次不用再输；
 * 后台页面的脚本和样式只给登录了的人，打开着有效分享的浏览器也带一个签过名的 Cookie，凭它取这些文件
 */
@Service
public class ScreenShareService {

    /** 能选的有效期（小时），0 是长期有效 */
    public static final List<Integer> HOURS = List.of(1, 24, 168, 720, 0);
    public static final int PASSWORD_MIN = 4, PASSWORD_MAX = 32, NOTE_MAX = 40, MAX_ACTIVE = 50;
    static final String ASSETS = "ota_sv";
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final String SECRET = "share.secret";
    private static final Duration COOKIE_MAX = Duration.ofDays(30);
    /** 密码连续输错：同一个 IP 对同一个分享 10 分钟里最多 8 次 */
    private static final int TRIES = 8;
    private static final long TRY_WINDOW_MS = 10 * 60_000L;

    private final ScreenShareRepo repo;
    private final SettingService settings;
    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Deque<Long>> wrong = new ConcurrentHashMap<>();
    private final Map<Long, Long> touched = new ConcurrentHashMap<>();
    private volatile byte[] key;

    public ScreenShareService(ScreenShareRepo repo, SettingService settings) {
        this.repo = repo;
        this.settings = settings;
    }

    public enum State { ACTIVE, EXPIRED, REVOKED }

    public State state(ScreenShare s) {
        if (s.getRevokedAt() != null) return State.REVOKED;
        return s.expired(LocalDateTime.now()) ? State.EXPIRED : State.ACTIVE;
    }

    // ==================== 后台：生成、列出、取消 ====================

    public ScreenShare create(String createdBy, String createdName, boolean all, Long appId, int hours, String password,
                              String note, String theme) {
        if (!HOURS.contains(hours)) throw ApiException.badRequest("BAD_HOURS", "有效期只能选 1 小时、1 天、7 天、30 天或长期有效");
        String pwd = password == null ? "" : password.trim();
        if (!pwd.isEmpty() && (pwd.length() < PASSWORD_MIN || pwd.length() > PASSWORD_MAX)) {
            throw ApiException.badRequest("BAD_PASSWORD", "访问密码 " + PASSWORD_MIN + "–" + PASSWORD_MAX + " 位");
        }
        String n = note == null ? "" : note.trim();
        if (n.length() > NOTE_MAX) throw ApiException.badRequest("BAD_NOTE", "备注最多 " + NOTE_MAX + " 个字");
        if (repo.countActive(createdBy, LocalDateTime.now()) >= MAX_ACTIVE) {
            throw ApiException.badRequest("TOO_MANY", "你的分享太多了（" + MAX_ACTIVE + " 个），先取消一些不用的");
        }
        LocalDateTime expires = hours <= 0 ? null : LocalDateTime.now().plusHours(hours);
        ScreenShare s = new ScreenShare(randomText(16), all ? ScreenShare.ALL : ScreenShare.APP, all ? null : appId, createdBy, createdName,
                expires, pwd.isEmpty() ? null : bcrypt.encode(pwd), randomText(9), n.isEmpty() ? null : n, "light".equals(theme) ? "light" : null);
        return repo.save(s);
    }

    /** 超级管理员看全部人的，成员只看自己的 */
    public List<ScreenShare> list(String username, boolean owner) {
        return owner ? repo.findTop200ByOrderByCreatedAtDesc() : repo.findTop200ByCreatedByIgnoreCaseOrderByCreatedAtDesc(username);
    }

    /** 自己的随时能取消，超级管理员能取消任何人的 */
    public ScreenShare revoke(Long id, String username, boolean owner) {
        ScreenShare s = repo.findById(id == null ? -1L : id)
                .filter(x -> owner || x.getCreatedBy().equalsIgnoreCase(username))
                .orElseThrow(() -> ApiException.notFound("NO_SHARE", "没有这个分享"));
        if (s.getRevokedAt() == null) {
            s.revoke(username);
            repo.save(s);
        }
        return s;
    }

    public Optional<ScreenShare> find(String token) {
        if (token == null || !TOKEN.matcher(token).matches()) return Optional.empty();
        return repo.findByToken(token);
    }

    // ==================== 打开分享：密码、Cookie、访问记录 ====================

    public enum Try { OK, WRONG, LOCKED }

    public Try unlock(ScreenShare s, String password, String ip) {
        String k = s.getId() + "|" + ip;
        long now = System.currentTimeMillis();
        Deque<Long> q = wrong.computeIfAbsent(k, x -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > TRY_WINDOW_MS) q.pollFirst();
            if (q.size() >= TRIES) return Try.LOCKED;
            if (password != null && !password.isEmpty() && bcrypt.matches(password.trim(), s.getPassHash())) {
                q.clear();
                return Try.OK;
            }
            q.addLast(now);
            if (wrong.size() > 20_000) wrong.clear();
            return q.size() >= TRIES ? Try.LOCKED : Try.WRONG;
        }
    }

    /** 这个浏览器输对过这个分享的密码 */
    public boolean unlocked(ScreenShare s, HttpServletRequest req) {
        String v = cookie(req, unlockName(s));
        if (v == null) return false;
        String[] p = v.split("\\.");
        return p.length == 2 && fresh(p[0]) && same(p[1], sign("u|" + s.getId() + "|" + p[0] + "|" + s.getStamp()));
    }

    public ResponseCookie unlockCookie(ScreenShare s, boolean secure) {
        long exp = cookieExp(s);
        return cookie(unlockName(s), exp + "." + sign("u|" + s.getId() + "|" + exp + "|" + s.getStamp()), "/", exp, secure);
    }

    /** 带着它能取后台页面的脚本和样式；只发给 /vue/app 下的文件 */
    public ResponseCookie assetCookie(ScreenShare s, boolean secure) {
        long exp = cookieExp(s);
        return cookie(ASSETS, s.getId() + "." + exp + "." + sign("a|" + s.getId() + "|" + exp + "|" + s.getStamp()), "/vue/app/", exp, secure);
    }

    /** 没带登录、带着有效分享的 Cookie 来取后台页面的脚本和样式 */
    public boolean assetsAllowed(HttpServletRequest req) {
        String v = cookie(req, ASSETS);
        if (v == null) return false;
        String[] p = v.split("\\.");
        if (p.length != 3 || !fresh(p[1])) return false;
        long id;
        try {
            id = Long.parseLong(p[0]);
        } catch (NumberFormatException e) {
            return false;
        }
        ScreenShare s = repo.findById(id).orElse(null);
        return s != null && state(s) == State.ACTIVE && same(p[2], sign("a|" + id + "|" + p[1] + "|" + s.getStamp()));
    }

    /** 这个浏览器这次打开过没有（关了浏览器再开算新的一次） */
    public boolean seen(ScreenShare s, HttpServletRequest req) {
        return cookie(req, "ota_sc" + s.getId()) != null;
    }

    public ResponseCookie seenCookie(ScreenShare s, boolean secure) {
        return ResponseCookie.from("ota_sc" + s.getId(), "1").path("/").httpOnly(true).secure(secure).sameSite("Lax").build();
    }

    /** 记一次打开；开着的页面每 10 秒取一次数据，最近访问时间一分钟最多写一次 */
    public void visited(ScreenShare s, boolean fresh) {
        long now = System.currentTimeMillis();
        Long last = touched.get(s.getId());
        if (!fresh && last != null && now - last < 60_000) return;
        touched.put(s.getId(), now);
        if (touched.size() > 5_000) touched.clear();
        repo.findById(s.getId()).ifPresent(x -> {
            x.viewed(fresh, LocalDateTime.now());
            repo.save(x);
        });
    }

    private static String unlockName(ScreenShare s) {
        return "ota_su" + s.getId();
    }

    private static long cookieExp(ScreenShare s) {
        long max = System.currentTimeMillis() / 1000 + COOKIE_MAX.toSeconds();
        if (s.getExpiresAt() == null) return max;
        long until = s.getExpiresAt().atZone(ZoneId.systemDefault()).toEpochSecond();
        return Math.min(max, until);
    }

    private static boolean fresh(String exp) {
        try {
            return Long.parseLong(exp) > System.currentTimeMillis() / 1000;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static ResponseCookie cookie(String name, String value, String path, long exp, boolean secure) {
        long age = Math.max(0, exp - System.currentTimeMillis() / 1000);
        return ResponseCookie.from(name, value).path(path).maxAge(age).httpOnly(true).secure(secure).sameSite("Lax").build();
    }

    private static String cookie(HttpServletRequest req, String name) {
        Cookie[] all = req.getCookies();
        if (all == null) return null;
        for (Cookie c : all) if (name.equals(c.getName())) return c.getValue();
        return null;
    }

    private String sign(String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key(), "HmacSHA256"));
            byte[] h = mac.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(h, 18));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean same(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    /** 签名用的密钥存在设置里，重启以后浏览器记住的密码照样有效 */
    private byte[] key() {
        byte[] k = key;
        if (k != null) return k;
        synchronized (this) {
            if (key != null) return key;
            String stored = settings.get(SECRET, null);
            if (stored == null || stored.isBlank()) {
                stored = randomText(32);
                settings.set(SECRET, stored);
            }
            key = stored.getBytes(StandardCharsets.UTF_8);
            return key;
        }
    }

    private String randomText(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }
}
