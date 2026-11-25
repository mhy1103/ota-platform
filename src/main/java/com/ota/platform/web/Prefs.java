package com.ota.platform.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ota.platform.admin.AdminUser;
import com.ota.platform.admin.AdminUserRepo;
import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.regex.Pattern;

/** 管理员、代理各自的个人偏好 */
@Component
public class Prefs {

    private static final Map<String, Set<String>> VALUES = Map.of(
            "mode", Set.of("light", "dark", "system"),
            "sidebar", Set.of("theme", "dark", "light"),
            "density", Set.of("comfortable", "compact"),
            "side", Set.of("mini"),
            "tabs", Set.of("show", "hide"),
            "motion", Set.of("on", "off"),
            "lang", Set.of("zh", "en"),
            // 数据大屏自己的深浅色，和后台的深浅色（mode）互不相干；没存是深色
            "screen", Set.of("light", "dark"),
            "agentSort", Set.of("today", "week", "total", "d7", "login", "created", "name"),
            "agentView", Set.of("grid", "list"));
    /** 排法（/ + ~）、卡片 ID，后面可以带宽度和拉过的高度：/trend:8、~ops:6x720（中间一版存的占几行 r2 也认）。ID 里也有 r、x，所以 ID 不贪心匹配 */
    private static final Pattern CARD = Pattern.compile("[/+~]?([a-z]{2,16}?)(?::\\d{1,2})?(?:r[1-3])?(?:x\\d{2,4})?");
    /** 看板卡片自己的数据：便签、今日目标；只在概览上用，不放进每个页面 */
    private static final Map<String, Integer> CARD_DATA = Map.of("note", 500, "goal", 7);

    private final AdminUserRepo admins;
    private final AgentRepo agents;
    private final ObjectMapper json = new ObjectMapper();
    private final java.util.concurrent.ConcurrentHashMap<String, Object> locks = new java.util.concurrent.ConcurrentHashMap<>();

    public Prefs(AdminUserRepo admins, AgentRepo agents) {
        this.admins = admins;
        this.agents = agents;
    }

    /** 登录的账号存过的偏好；没登录、没存过是 null */
    @Transactional(readOnly = true)
    public String stored(Authentication auth) {
        if (admin(auth)) return admins.findByUsernameIgnoreCase(auth.getName()).map(AdminUser::getPrefs).orElse(null);
        if (agent(auth)) return agents.findByUsername(auth.getName()).map(Agent::getPrefs).orElse(null);
        return null;
    }

    public String saveUrl(Authentication auth) {
        if (admin(auth)) return "/admin/account/prefs";
        if (agent(auth)) return "/agent/account/prefs";
        return null;
    }

    /** 空值恢复默认，不认识的项不存 */
    public String merge(String stored, Map<String, String> changes) {
        ObjectNode o = read(stored);
        changes.forEach((k, v) -> {
            Set<String> ok = VALUES.get(k);
            if (ok == null) return;
            if (v == null || v.isBlank()) o.remove(k);
            else if (ok.contains(v)) o.put(k, v);
        });
        return o.toString();
    }

    /** 概览上显示哪些卡片、什么顺序；没调整过是 null */
    public List<String> board(String stored) {
        JsonNode b = read(stored).get("board");
        if (b == null || !b.isArray()) return null;
        List<String> out = new ArrayList<>();
        b.forEach(n -> { if (CARD.matcher(n.asText()).matches()) out.add(n.asText()); });
        return out;
    }

    public String withBoard(String stored, List<String> cards) {
        ObjectNode o = read(stored);
        if (cards == null) {
            o.remove("board");
        } else {
            ArrayNode a = o.putArray("board");
            Set<String> seen = new java.util.HashSet<>();
            cards.stream().filter(c -> {
                java.util.regex.Matcher m = CARD.matcher(c);
                return m.matches() && seen.add(m.group(1));
            }).limit(40).forEach(a::add);
        }
        return o.toString();
    }

    /** 看板布局的指纹：别的设备、别的标签页拿它判断布局有没有变 */
    public static String boardSig(List<String> board) {
        return board == null ? "default" : Integer.toHexString(String.join(",", board).hashCode());
    }

    @Transactional(readOnly = true)
    public String boardSigOf(Long adminId) {
        return boardSig(admins.findById(adminId).map(u -> board(u.getPrefs())).orElse(null));
    }

    public String cardData(String stored, String key) {
        JsonNode v = read(stored).get(key);
        return v == null || !v.isTextual() ? null : v.asText();
    }

    /** 空值删掉；goal 只能是数字 */
    public String withCardData(String stored, String key, String value) {
        Integer max = CARD_DATA.get(key);
        if (max == null) return stored;
        ObjectNode o = read(stored);
        String v = value == null ? "" : value.strip();
        if ("goal".equals(key) && !v.matches("\\d{0,7}")) return o.toString();
        if (v.isEmpty() || "goal".equals(key) && Long.parseLong(v) == 0) o.remove(key);
        else o.put(key, v.length() > max ? v.substring(0, max) : v);
        return o.toString();
    }

    /** 加载壳里给页面脚本的偏好：去掉看板布局、卡片数据和外观（外观在服务端换成主题 CSS） */
    public String forPage(String stored) {
        ObjectNode o = read(stored);
        o.remove("board");
        o.remove(LOOK);
        CARD_DATA.keySet().forEach(o::remove);
        return o.toString();
    }

    // ==================== 成员自己选的外观 ====================

    private static final String LOOK = "look";
    private static final Pattern LOOK_VALUE = Pattern.compile("[a-z0-9-]{1,20}");
    private static final long LOOK_TTL_MS = 5 * 60_000;
    private record CachedLook(Map<String, String> look, long at) {}
    private final java.util.concurrent.ConcurrentHashMap<String, CachedLook> looks = new java.util.concurrent.ConcurrentHashMap<>();

    /** 存着的外观（主题色、圆角、侧栏……），没选过是 null；值对不对由 SettingService 再核对 */
    public Map<String, String> look(String stored) {
        JsonNode l = read(stored).get(LOOK);
        if (l == null || !l.isObject()) return null;
        Map<String, String> out = new java.util.LinkedHashMap<>();
        l.fields().forEachRemaining(e -> {
            if (e.getValue().isTextual() && LOOK_VALUE.matcher(e.getValue().asText()).matches()) out.put(e.getKey(), e.getValue().asText());
        });
        return out.isEmpty() ? null : out;
    }

    /** look 为空表示不再自己选，跟着默认外观走 */
    public String withLook(String stored, Map<String, String> look) {
        ObjectNode o = read(stored);
        if (look == null || look.isEmpty()) {
            o.remove(LOOK);
        } else {
            ObjectNode l = o.putObject(LOOK);
            look.forEach((k, v) -> { if (v != null && LOOK_VALUE.matcher(v).matches()) l.put(k, v); });
        }
        return o.toString();
    }

    /** 每个后台页面都要：这个成员自己选的外观，超级管理员、代理、没选过的是 null。查一次记 5 分钟，自己改了马上作废 */
    @Transactional(readOnly = true)
    public Map<String, String> memberLook(Authentication auth) {
        return admin(auth) ? lookOf(auth.getName()) : null;
    }

    /** 这个管理员自己选的外观（分享出去的数据大屏按分享人的外观显示） */
    @Transactional(readOnly = true)
    public Map<String, String> lookOf(String username) {
        if (username == null) return null;
        String key = username.toLowerCase(java.util.Locale.ROOT);
        CachedLook c = looks.get(key);
        if (c != null && System.currentTimeMillis() - c.at() < LOOK_TTL_MS) return c.look();
        Map<String, String> look = admins.findByUsernameIgnoreCase(username)
                .filter(u -> !u.isOwner())
                .map(u -> look(u.getPrefs()))
                .orElse(null);
        if (looks.size() > 500) looks.clear();
        looks.put(key, new CachedLook(look, System.currentTimeMillis()));
        return look;
    }

    public void forgetLook(String username) {
        if (username != null) looks.remove(username.toLowerCase(java.util.Locale.ROOT));
    }

    // 同一账号并发保存时串行，免得后存的盖掉先存的
    public void updateAdmin(Long id, UnaryOperator<String> change) {
        synchronized (lock("a" + id)) {
            admins.findById(id).ifPresent(u -> {
                u.setPrefs(change.apply(u.getPrefs()));
                admins.save(u);
            });
        }
    }

    public void updateAgent(Long id, UnaryOperator<String> change) {
        synchronized (lock("g" + id)) {
            agents.findById(id).ifPresent(a -> {
                a.setPrefs(change.apply(a.getPrefs()));
                agents.save(a);
            });
        }
    }

    private Object lock(String key) {
        return locks.computeIfAbsent(key, k -> new Object());
    }

    private ObjectNode read(String s) {
        try {
            JsonNode n = s == null || s.isBlank() ? null : json.readTree(s);
            return n instanceof ObjectNode o ? o : json.createObjectNode();
        } catch (Exception e) {
            return json.createObjectNode();
        }
    }

    private static boolean signedIn(Authentication auth) {
        return auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
    }

    private static boolean admin(Authentication auth) {
        return signedIn(auth) && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static boolean agent(Authentication auth) {
        return signedIn(auth) && auth.getAuthorities().stream().anyMatch(a -> "ROLE_AGENT".equals(a.getAuthority()));
    }
}
