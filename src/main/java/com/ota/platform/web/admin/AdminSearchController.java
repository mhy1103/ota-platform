package com.ota.platform.web.admin;

import com.ota.platform.web.AdminUi;
import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AdminUser;
import com.ota.platform.admin.AdminUserService;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.app.OtaApp;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseRepository;
import com.ota.platform.stats.DeviceSeen;
import com.ota.platform.stats.DeviceSeenRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * ⌘K 里直接搜数据：口令（所有 App 通用），当前 App 的设备、版本，代理，成员，App。
 * 只搜当前登录的人能看到的：当前 App 要有查看权限，代理要能管理代理，成员只有超级管理员能搜。
 */
@RestController
@RequestMapping("/admin/search")
public class AdminSearchController {

    private static final int LIMIT = 6;

    public record Item(String title, String sub, String href, String icon) {}

    public record Group(String title, List<Item> items) {}

    private final AdminShell shell;
    private final AccessService access;
    private final ActivationCodeRepo codes;
    private final AgentRepo agents;
    private final DeviceSeenRepository devices;
    private final ReleaseRepository releases;
    private final AdminUserService admins;
    private final AdminUi ui;

    public AdminSearchController(AdminShell shell, AccessService access, ActivationCodeRepo codes, AgentRepo agents,
                                 DeviceSeenRepository devices, ReleaseRepository releases, AdminUserService admins, AdminUi ui) {
        this.shell = shell;
        this.access = access;
        this.codes = codes;
        this.agents = agents;
        this.devices = devices;
        this.releases = releases;
        this.admins = admins;
        this.ui = ui;
    }

    @GetMapping
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q, HttpSession session) {
        String query = q.strip();
        CurrentAdmin me = access.current();
        if (me == null || query.length() < 2 || query.length() > 64) return Map.of("q", query, "groups", List.of());
        String needle = query.toLowerCase(Locale.ROOT);
        List<Group> groups = new ArrayList<>();
        OtaApp app = shell.current(session);
        boolean seeApp = app != null && me.canSee(app.getId());
        Map<Long, String> agentNames = new HashMap<>();
        agents.findAll().forEach(a -> agentNames.put(a.getId(), a.getName()));
        String like = "%" + needle.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";

        Specification<ActivationCode> spec = (root, cq, cb) ->
                cb.or(cb.like(cb.lower(root.get("code")), like, '\\'), cb.like(cb.lower(root.get("remark")), like, '\\'));
        add(groups, "口令", codes.findAll(spec, PageRequest.of(0, LIMIT, Sort.by(Sort.Direction.DESC, "createdAt"))).stream()
                .map(c -> new Item(c.getCode(), join(codeStatus(c), agentNames.getOrDefault(c.getAgentId(), "未分配"), c.getRemark()),
                        "/admin/codes?q=" + enc(c.getCode()), "#i-key"))
                .toList());

        if (seeApp) {
            Specification<DeviceSeen> dev = (root, cq, cb) -> cb.and(cb.equal(root.get("appId"), app.getId()),
                    cb.like(cb.lower(root.get("clientId")), like, '\\'));
            add(groups, "设备", devices.findAll(dev, PageRequest.of(0, LIMIT, Sort.by(Sort.Direction.DESC, "lastSeen"))).stream()
                    .map(d -> new Item(d.getClientId(), join(d.getPlatform(), d.getChannel(), "最近 " + ui.ago(d.getLastSeen())),
                            "/admin/devices?q=" + enc(d.getClientId()), "#i-phone"))
                    .toList());

            add(groups, "版本", releases.findByAppIdOrderByCreatedAtDesc(app.getId()).stream()
                    .filter(r -> r.getId().toLowerCase(Locale.ROOT).startsWith(needle)
                            || (r.getMessage() != null && r.getMessage().toLowerCase(Locale.ROOT).contains(needle)))
                    .limit(LIMIT)
                    .map(r -> new Item(ui.rid(r.getId()) + (r.getMessage() == null || r.getMessage().isBlank() ? "" : " · " + r.getMessage()),
                            join("Runtime " + r.getRuntimeVersion(), ui.ago(r.getCreatedAt())), "/admin/releases#rel-" + r.getId(), "#i-layers"))
                    .toList());
        }

        if (me.canManageAgents()) {
            add(groups, "代理", agents.findAll().stream()
                    .filter(a -> contains(a.getName(), needle) || contains(a.getUsername(), needle))
                    .limit(LIMIT)
                    .map(a -> new Item(a.getName(), join(a.getUsername(), Boolean.TRUE.equals(a.getEnabled()) ? "启用" : "已停用"),
                            "/admin/agents#agent-" + a.getId(), "#i-users"))
                    .toList());
        }

        if (me.owner()) {
            add(groups, "成员", admins.all().stream()
                    .filter(u -> contains(u.getName(), needle) || contains(u.getUsername(), needle))
                    .limit(LIMIT)
                    .map(u -> new Item(u.getName(), join(u.getUsername(), u.isOwner() ? "超级管理员" : "成员", u.isEnabled() ? null : "已停用"),
                            "/admin/admins#admin-" + u.getId(), "#i-user-cog"))
                    .toList());
        }

        add(groups, "App", shell.visible().stream()
                .filter(a -> contains(a.getName(), needle) || contains(a.getSlug(), needle))
                .limit(LIMIT)
                .map(a -> new Item(a.getName(), join(a.getSlug(), app != null && a.getId().equals(app.getId()) ? "当前" : "切换到它"),
                        "/admin/use?app=" + enc(a.getSlug()), "#i-package"))
                .toList());

        if (seeApp) {
            add(groups, "在页面里搜", List.of(
                    new Item("在激活记录里搜「" + query + "」", "按设备 ID、IP 或口令", "/admin/activations?q=" + enc(query), "#i-activity"),
                    new Item("在操作日志里搜「" + query + "」", "按内容或操作人", "/admin/logs?q=" + enc(query), "#i-file")));
        }
        return Map.of("q", query, "groups", groups);
    }

    private static void add(List<Group> groups, String title, List<Item> items) {
        if (!items.isEmpty()) groups.add(new Group(title, items));
    }

    private static boolean contains(String s, String needle) {
        return s != null && s.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static String join(String... parts) {
        List<String> out = new ArrayList<>();
        for (String p : parts) if (p != null && !p.isBlank()) out.add(p.strip());
        return String.join(" · ", out);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String codeStatus(ActivationCode c) {
        if (!Boolean.TRUE.equals(c.getEnabled())) return "停用";
        if (c.getExpiresAt() != null && c.getExpiresAt().isBefore(LocalDateTime.now())) return "已过期";
        Integer max = c.getMaxUses();
        if (max != null && max > 0 && c.limitUsed() >= max) return "已用完";
        return "可用";
    }
}
