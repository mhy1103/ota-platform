package com.ota.platform.admin;

import com.ota.platform.agent.AgentRepo;
import com.ota.platform.common.ApiException;
import com.ota.platform.settings.SettingService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** 管理员账号和授权 */
@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_.\\-]{3,32}");
    private static final Pattern GUIDE_KEY = Pattern.compile("[a-z][a-z0-9:-]{0,39}");

    private final AdminUserRepo users;
    private final AdminGrantRepo grants;
    private final PasswordEncoder encoder;
    private final SettingService settings;
    private final AgentRepo agents;

    public AdminUserService(AdminUserRepo users, AdminGrantRepo grants, PasswordEncoder encoder, SettingService settings, AgentRepo agents) {
        this.users = users;
        this.grants = grants;
        this.encoder = encoder;
        this.settings = settings;
        this.agents = agents;
    }

    /** 从只有一个管理员账号的老版本升级时，把它变成超级管理员（账号密码不变）。新装的走安装引导 */
    @PostConstruct
    void migrate() {
        if (users.count() > 0 || settings.adminPasswordHash() == null) return;
        AdminUser u = new AdminUser();
        u.setUsername(settings.adminUsername());
        u.setName(settings.adminUsername());
        u.setPasswordHash(settings.adminPasswordHash());
        u.setOwner(true);
        users.save(u);
        log.info("已把管理员账号 {} 设为超级管理员", u.getUsername());
    }

    /** 新建、编辑管理员的表单。password 为空表示不改；grants 是成员的 App 授权 */
    public record Form(String username, String name, String password, boolean owner, boolean manageAgents, boolean manageCodes,
                       boolean enabled, String remark, Map<Long, Set<AppPerm>> grants, boolean forceChange) {}

    public List<AdminUser> all() {
        return users.findAllByOrderByOwnerDescCreatedAtAsc();
    }

    public Optional<AdminUser> find(String username) {
        return username == null ? Optional.empty() : users.findByUsernameIgnoreCase(username.trim());
    }

    public AdminUser require(Long id) {
        return users.findById(id == null ? -1L : id).orElseThrow(() -> ApiException.notFound("ADMIN_NOT_FOUND", "管理员不存在"));
    }

    public Map<Long, Set<AppPerm>> grantsOf(Long adminId) {
        Map<Long, Set<AppPerm>> out = new HashMap<>();
        for (AdminGrant g : grants.findByAdminId(adminId)) out.put(g.getAppId(), AppPerm.parse(g.getPerms()));
        return out;
    }

    /** 账号是否已被管理员或代理占用 */
    public boolean usernameTaken(String username) {
        return username != null && (users.existsByUsernameIgnoreCase(username.trim()) || agents.existsByUsername(username.trim().toLowerCase(Locale.ROOT)));
    }

    /** 超级管理员只有一个（安装时建的），新建的都是成员 */
    @Transactional
    public AdminUser create(Form f) {
        String username = checkUsername(f.username());
        if (usernameTaken(username)) throw ApiException.conflict("USERNAME_TAKEN", "账号 " + username + " 已被管理员或代理使用");
        AdminUser u = new AdminUser();
        u.setUsername(username);
        u.setName(checkName(f.name(), username));
        u.setPasswordHash(encoder.encode(checkPassword(f.password())));
        u.setOwner(false);
        u.setManageAgents(f.manageAgents());
        u.setManageCodes(f.manageCodes());
        u.setEnabled(f.enabled());
        u.setRemark(trimTo(f.remark(), 200));
        u.setMustChangePassword(f.forceChange());
        users.save(u);
        saveGrants(u, f.grants());
        return u;
    }

    @Transactional
    public AdminUser update(Long id, Form f, CurrentAdmin by) {
        AdminUser u = require(id);
        boolean self = by != null && u.getId().equals(by.id());
        String username = checkUsername(f.username());
        if (!username.equalsIgnoreCase(u.getUsername()) && usernameTaken(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "账号 " + username + " 已被管理员或代理使用");
        }
        if (self && !f.enabled()) throw ApiException.badRequest("SELF", "不能停用自己的账号");
        if (self && u.isOwner() && !f.owner()) throw ApiException.badRequest("SELF", "不能取消自己的超级管理员");
        // 超级管理员只能有一个：成员不能升成超级管理员；以前多出来的超级管理员可以改回成员
        boolean owner = u.isOwner() && f.owner();
        boolean losesOwner = u.isOwner() && u.isEnabled() && (!owner || !f.enabled());
        if (losesOwner && users.countByOwnerTrueAndEnabledTrue() <= 1) {
            throw ApiException.badRequest("LAST_OWNER", "至少要保留一个启用的超级管理员");
        }
        boolean relogin = !username.equalsIgnoreCase(u.getUsername()) || (u.isEnabled() && !f.enabled());
        u.setUsername(username);
        u.setName(checkName(f.name(), username));
        u.setOwner(owner);
        u.setManageAgents(!owner && f.manageAgents());
        u.setManageCodes(!owner && f.manageCodes());
        u.setEnabled(f.enabled());
        u.setRemark(trimTo(f.remark(), 200));
        if (f.password() != null && !f.password().isEmpty()) {
            u.setPasswordHash(encoder.encode(checkPassword(f.password())));
            u.setMustChangePassword(!self && f.forceChange());
            relogin = true;
        }
        if (relogin) u.setCredentialsAt(Instant.now());
        users.save(u);
        saveGrants(u, f.grants());
        return u;
    }

    /** 只改启用状态：不能停用自己，至少留一个启用的超级管理员；停用后 TA 要重新登录 */
    @Transactional
    public AdminUser setEnabled(Long id, boolean enabled, CurrentAdmin by) {
        AdminUser u = require(id);
        if (u.isEnabled() == enabled) return u;
        if (!enabled && by != null && u.getId().equals(by.id())) throw ApiException.badRequest("SELF", "不能停用自己的账号");
        if (!enabled && u.isOwner() && users.countByOwnerTrueAndEnabledTrue() <= 1) {
            throw ApiException.badRequest("LAST_OWNER", "至少要保留一个启用的超级管理员");
        }
        u.setEnabled(enabled);
        if (!enabled) u.setCredentialsAt(Instant.now());
        users.save(u);
        return u;
    }

    @Transactional
    public AdminUser delete(Long id, CurrentAdmin by) {
        AdminUser u = require(id);
        if (by != null && u.getId().equals(by.id())) throw ApiException.badRequest("SELF", "不能删除自己的账号");
        if (u.isOwner() && u.isEnabled() && users.countByOwnerTrueAndEnabledTrue() <= 1) {
            throw ApiException.badRequest("LAST_OWNER", "至少要保留一个启用的超级管理员");
        }
        grants.deleteByAdminId(u.getId());
        users.delete(u);
        return u;
    }

    /** 自己改名字、账号、密码；改了账号或密码返回 true（要重新登录） */
    @Transactional
    public boolean updateSelf(Long id, String name, String username, String currentPassword, String newPassword) {
        AdminUser u = require(id);
        if (currentPassword == null || !encoder.matches(currentPassword, u.getPasswordHash())) {
            throw ApiException.badRequest("BAD_PASSWORD", "当前密码不正确");
        }
        String uname = checkUsername(username == null || username.isBlank() ? u.getUsername() : username);
        boolean renamed = !uname.equalsIgnoreCase(u.getUsername());
        if (renamed && usernameTaken(uname)) throw ApiException.conflict("USERNAME_TAKEN", "账号 " + uname + " 已被管理员或代理使用");
        boolean changePassword = newPassword != null && !newPassword.isEmpty();
        u.setName(checkName(name == null || name.isBlank() ? u.getName() : name, uname));
        u.setUsername(uname);
        if (changePassword) {
            u.setPasswordHash(encoder.encode(checkPassword(newPassword)));
            u.setMustChangePassword(false);
        }
        if (renamed || changePassword) u.setCredentialsAt(Instant.now());
        users.save(u);
        return renamed || changePassword;
    }

    /** 记下看过的引导。标识只认小写字母、数字、冒号和横线，列表超长时先丢最早的页面引导 */
    @Transactional
    public void markGuide(Long id, String key) {
        if (key == null || !GUIDE_KEY.matcher(key).matches()) throw ApiException.badRequest("BAD_KEY", "引导标识不对");
        users.findById(id).ifPresent(u -> {
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            if (u.getGuide() != null) {
                for (String k : u.getGuide().split(",")) if (!k.isBlank()) seen.add(k.trim());
            }
            if (!seen.add(key)) return;
            String joined = String.join(",", seen);
            while (joined.length() > 400) {
                String drop = seen.stream().filter(k -> !k.equals("welcome") && !k.equals(key)).findFirst().orElse(null);
                if (drop == null) break;
                seen.remove(drop);
                joined = String.join(",", seen);
            }
            u.setGuide(joined);
            users.save(u);
        });
    }

    @Transactional
    public void resetGuide(Long id) {
        users.findById(id).ifPresent(u -> {
            u.setGuide(null);
            users.save(u);
        });
    }

    public boolean passwordMatches(Long id, String password) {
        return password != null && users.findById(id).map(u -> encoder.matches(password, u.getPasswordHash())).orElse(false);
    }

    @Transactional
    public void recordLogin(String username) {
        users.findByUsernameIgnoreCase(username).ifPresent(u -> {
            u.setLastLoginAt(Instant.now());
            users.save(u);
        });
    }

    /** App 删除后清掉它的授权 */
    @Transactional
    public void dropApp(Long appId) {
        grants.deleteByAppId(appId);
    }

    private void saveGrants(AdminUser u, Map<Long, Set<AppPerm>> wanted) {
        grants.deleteByAdminId(u.getId());
        grants.flush();
        if (u.isOwner() || wanted == null) return;
        for (Map.Entry<Long, Set<AppPerm>> e : wanted.entrySet()) {
            grants.save(new AdminGrant(u.getId(), e.getKey(), AppPerm.join(e.getValue())));
        }
    }

    private static String checkUsername(String username) {
        String s = username == null ? "" : username.trim();
        if (!USERNAME.matcher(s).matches()) throw ApiException.badRequest("BAD_USERNAME", "账号要 3–32 位字母、数字或 _ . -");
        return s;
    }

    private static String checkName(String name, String fallback) {
        String n = name == null || name.isBlank() ? fallback : name.trim();
        return n.length() > 50 ? n.substring(0, 50) : n;
    }

    private static String checkPassword(String password) {
        if (password == null || password.length() < 8) throw ApiException.badRequest("BAD_PASSWORD", "密码至少 8 位");
        if (password.length() > 72) throw ApiException.badRequest("BAD_PASSWORD", "密码最多 72 位");
        return password;
    }

    private static String trimTo(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
