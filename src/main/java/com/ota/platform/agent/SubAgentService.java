package com.ota.platform.agent;

import com.ota.platform.admin.AdminUserService;
import com.ota.platform.audit.AuditService;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.common.ApiException;
import com.ota.platform.settings.SettingService;
import com.ota.platform.web.AdminUi;
import com.ota.platform.web.agent.Settlement;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 一级代理在代理后台管理自己的二级代理：开账号、改设置、分口令、停用、删除。
 * 二级代理和代理一样有扣量、数据更新方式、能不能看设备记录，但都不能比上级宽：上级看不了设备记录，下面也开不了；
 * 数据更新不能比上级新；手动更新最多只更新到上级能看到的时间。口令仍归上级，只是标上分给了谁
 */
@Service
public class SubAgentService {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_.-]{3,32}$");

    private final AgentRepo agents;
    private final ActivationCodeRepo codes;
    private final PasswordEncoder encoder;
    private final SettingService settings;
    private final AdminUserService adminUsers;
    private final AuditService audit;
    private final AdminUi ui;
    private final Settlement settlement;
    private final com.ota.platform.web.agent.CodeLimits limits;
    private final com.ota.platform.notice.NoticeService notices;

    public SubAgentService(AgentRepo agents, ActivationCodeRepo codes, PasswordEncoder encoder, SettingService settings,
                           AdminUserService adminUsers, AuditService audit, AdminUi ui, Settlement settlement,
                           com.ota.platform.web.agent.CodeLimits limits, com.ota.platform.notice.NoticeService notices) {
        this.notices = notices;
        this.settlement = settlement;
        this.limits = limits;
        this.agents = agents;
        this.codes = codes;
        this.encoder = encoder;
        this.settings = settings;
        this.adminUsers = adminUsers;
        this.audit = audit;
        this.ui = ui;
    }

    /** 表单。tiersJson 是扣量档位（和代理一样的格式），null 是不扣量 */
    public record Form(String name, String username, String password, boolean canViewRecords, String dataMode,
                       String tiersJson, String remark) {}

    /** 保存的结果：tiersChanged 是扣量档位改了（从现在起按新的算） */
    public record Saved(Agent sub, boolean tiersChanged) {}

    /** 二级代理能选的数据更新方式：不能比上级新 */
    public static List<String> modesUnder(Agent p) {
        return switch (p.getDataMode()) {
            case Agent.MODE_DAILY -> List.of(Agent.MODE_DAILY, Agent.MODE_MANUAL);
            case Agent.MODE_MANUAL -> List.of(Agent.MODE_MANUAL);
            default -> List.of(Agent.MODE_REALTIME, Agent.MODE_DAILY, Agent.MODE_MANUAL);
        };
    }

    /** 能不能登录、能不能用：自己启用，二级代理还要上级启用、上级还有开二级代理的权限 */
    public static boolean active(Agent a, Agent parent) {
        if (!Boolean.TRUE.equals(a.getEnabled())) return false;
        if (!a.isSub()) return true;
        return parent != null && Boolean.TRUE.equals(parent.getEnabled()) && parent.canManageSubs();
    }

    public boolean active(Agent a) {
        return active(a, a.isSub() ? agents.findById(a.getParentId()).orElse(null) : null);
    }

    public List<Agent> subsOf(Agent p) {
        return agents.findByParentIdOrderByCreatedAtDesc(p.getId());
    }

    @Transactional
    public Agent create(Agent p, Form f) {
        requireManager(p);
        int limit = p.getSubAgentLimit();
        if (limit > 0 && agents.countByParentId(p.getId()) >= limit) {
            throw ApiException.badRequest("SUB_LIMIT", "最多开 " + limit + " 个二级代理，已经开满了，要多开找管理员");
        }
        String login = normalize(f.username());
        validate(p, null, f, login, true);
        Agent s = new Agent();
        s.setParentId(p.getId());
        s.setRequestMode(Agent.REQUEST_OFF);
        apply(p, s, f, login);
        s.setPasswordHash(encoder.encode(f.password()));
        agents.save(s);
        settlement.created(s);
        audit.logAs(operator(p), null, "SUB_AGENT_CREATE", "代理 " + p.getName() + " 新建二级代理 " + s.getName() + "（" + s.getUsername() + "）" + describe(s));
        return s;
    }

    @Transactional
    public Saved update(Agent p, Long id, Form f) {
        requireManager(p);
        Agent s = mine(p, id);
        String login = normalize(f.username());
        validate(p, s, f, login, false);
        String before = s.getDeductionTiers();
        apply(p, s, f, login);
        boolean reset = f.password() != null && !f.password().isEmpty();
        if (reset) s.setPasswordHash(encoder.encode(f.password()));
        agents.save(s);
        boolean changed = settlement.tiersChanged(s, before);
        if (!changed) settlement.settle(s);
        audit.logAs(operator(p), null, "SUB_AGENT_UPDATE", "代理 " + p.getName() + " 修改二级代理 " + s.getName() + "（" + s.getUsername() + "）"
                + describe(s) + (reset ? " · 重置密码" : ""));
        return new Saved(s, changed);
    }

    @Transactional
    public Agent toggle(Agent p, Long id) {
        requireManager(p);
        Agent s = mine(p, id);
        s.setEnabled(!Boolean.TRUE.equals(s.getEnabled()));
        agents.save(s);
        audit.logAs(operator(p), null, "SUB_AGENT_TOGGLE", "代理 " + p.getName() + (s.getEnabled() ? " 启用" : " 停用") + "二级代理 " + s.getName());
        return s;
    }

    /** 手动更新：把二级代理看到的数据推进到现在，但不超过上级自己能看到的时间 */
    @Transactional
    public Agent refresh(Agent p, Long id) {
        requireManager(p);
        Agent s = mine(p, id);
        if (!Agent.MODE_MANUAL.equals(s.getDataMode())) throw ApiException.badRequest("NOT_MANUAL", s.getName() + " 不是手动更新，不用更新");
        s.setSnapshotAt(upTo(p));
        agents.save(s);
        settlement.settle(p);
        audit.logAs(operator(p), null, "SUB_AGENT_REFRESH", "代理 " + p.getName() + " 更新二级代理 " + s.getName() + " 的数据，截至 " + ui.dts(s.getSnapshotAt()));
        return s;
    }

    /** 所有手动更新的二级代理一起更新，返回更新了几个 */
    @Transactional
    public int refreshAll(Agent p) {
        requireManager(p);
        LocalDateTime at = upTo(p);
        List<Agent> manual = subsOf(p).stream().filter(s -> Agent.MODE_MANUAL.equals(s.getDataMode())).toList();
        manual.forEach(s -> s.setSnapshotAt(at));
        agents.saveAll(manual);
        if (!manual.isEmpty()) {
            settlement.settle(p);
            audit.logAs(operator(p), null, "SUB_AGENT_REFRESH", "代理 " + p.getName() + " 更新全部 " + manual.size() + " 个手动二级代理的数据，截至 " + ui.dts(at));
        }
        return manual.size();
    }

    /** 删除二级代理：分给 TA 的口令回到上级手里，激活记录不动。返回收回的口令个数 */
    @Transactional
    public int delete(Agent p, Long id) {
        requireManager(p);
        Agent s = mine(p, id);
        int back = release(s);
        settlement.forget(List.of(s.getId()));
        notices.forget(List.of(s.getId()));
        agents.delete(s);
        audit.logAs(operator(p), null, "SUB_AGENT_DELETE", "代理 " + p.getName() + " 删除二级代理 " + s.getName() + "（" + s.getUsername() + "）"
                + (back > 0 ? "，" + back + " 个口令收回" : ""));
        return back;
    }

    /** 分给 TA 的口令都收回给上级 */
    @Transactional
    public int release(Agent s) {
        List<ActivationCode> given = codes.findBySubAgentIdOrderByCreatedAtDesc(s.getId());
        given.forEach(c -> c.setSubAgentId(null));
        codes.saveAll(given);
        limits.touch(s.getParentId());
        return given.size();
    }

    public record Moved(Agent sub, int given, int taken) {}

    /** 分口令：give 是从上级自己手里（还没分出去的）分给 TA 的，take 是从 TA 手里收回的 */
    @Transactional
    public Moved assign(Agent p, Long id, Collection<Long> give, Collection<Long> take) {
        requireManager(p);
        Agent s = mine(p, id);
        int given = 0, taken = 0;
        List<ActivationCode> changed = new ArrayList<>();
        if (give != null && !give.isEmpty()) {
            for (ActivationCode c : codes.findAllById(new HashSet<>(give))) {
                if (!p.getId().equals(c.getAgentId())) continue;
                if (c.getSubAgentId() != null) {
                    if (c.getSubAgentId().equals(s.getId())) continue;
                    throw ApiException.badRequest("CODE_GIVEN", "口令 " + c.getCode() + " 已经分给别的二级代理了，先从 TA 那里收回");
                }
                c.setSubAgentId(s.getId());
                changed.add(c);
                given++;
            }
        }
        if (take != null && !take.isEmpty()) {
            for (ActivationCode c : codes.findAllById(new HashSet<>(take))) {
                if (!p.getId().equals(c.getAgentId()) || !s.getId().equals(c.getSubAgentId())) continue;
                c.setSubAgentId(null);
                changed.add(c);
                taken++;
            }
        }
        if (changed.isEmpty()) throw ApiException.badRequest("NO_CODES", "没有选口令");
        codes.saveAll(changed);
        limits.touch(p.getId());
        audit.logAs(operator(p), null, "SUB_AGENT_CODES", "代理 " + p.getName() + " 给二级代理 " + s.getName()
                + (given > 0 ? " 分了 " + given + " 个口令" : "") + (given > 0 && taken > 0 ? "，" : "") + (taken > 0 ? " 收回 " + taken + " 个口令" : ""));
        return new Moved(s, given, taken);
    }

    /** TA 的二级代理；不是 TA 的当作没有 */
    public Agent mine(Agent p, Long id) {
        return agents.findById(id == null ? -1L : id).filter(s -> p.getId().equals(s.getParentId()))
                .orElseThrow(() -> ApiException.notFound("NO_SUB_AGENT", "没有这个二级代理"));
    }

    private static void requireManager(Agent p) {
        if (!p.canManageSubs()) throw ApiException.forbidden("管理员没有给你开二级代理");
    }

    private void validate(Agent p, Agent self, Form f, String login, boolean requirePassword) {
        String name = f.name() == null ? "" : f.name().trim();
        if (name.isEmpty()) throw ApiException.badRequest("BAD_NAME", "请填写名称");
        if (name.length() > 64) throw ApiException.badRequest("BAD_NAME", "名称最多 64 个字");
        if (!USERNAME.matcher(login).matches()) throw ApiException.badRequest("BAD_USERNAME", "登录账号需为 3–32 位小写字母、数字或 _ . -");
        if (settings.isAdminUsername(login) || adminUsers.find(login).isPresent()) {
            throw ApiException.badRequest("USERNAME_TAKEN", "登录账号 " + login + " 已被使用");
        }
        boolean taken = agents.findByUsername(login).filter(o -> self == null || !o.getId().equals(self.getId())).isPresent();
        if (taken) throw ApiException.badRequest("USERNAME_TAKEN", "登录账号 " + login + " 已被使用");
        String pwd = f.password() == null ? "" : f.password();
        if ((requirePassword || !pwd.isEmpty()) && (pwd.length() < 6 || pwd.length() > 64)) {
            throw ApiException.badRequest("BAD_PASSWORD", "密码 6–64 位");
        }
        if (!modesUnder(p).contains(f.dataMode())) {
            throw ApiException.badRequest("BAD_MODE", "数据更新不能比你自己的新：你是" + ui.dataMode(p.getDataMode()) + "，二级代理只能选"
                    + String.join("、", modesUnder(p).stream().map(ui::dataMode).toList()));
        }
        if (f.canViewRecords() && !Boolean.TRUE.equals(p.getCanViewRecords())) {
            throw ApiException.badRequest("NO_RECORDS", "你自己看不了设备记录，二级代理也开不了");
        }
        if (f.remark() != null && f.remark().trim().length() > 200) throw ApiException.badRequest("BAD_REMARK", "备注最多 200 字");
    }

    private void apply(Agent p, Agent s, Form f, String login) {
        boolean becameManual = Agent.MODE_MANUAL.equals(f.dataMode()) && !Agent.MODE_MANUAL.equals(s.getDataMode());
        s.setName(f.name().trim());
        s.setUsername(login);
        s.setCanViewRecords(f.canViewRecords());
        s.setDataMode(f.dataMode());
        s.setDeductionTiers(f.tiersJson());
        s.setRemark(f.remark() == null ? "" : f.remark().trim());
        if (becameManual || (Agent.MODE_MANUAL.equals(s.getDataMode()) && s.getSnapshotAt() == null)) s.setSnapshotAt(upTo(p));
    }

    /** 上级自己能看到的时间（实时是现在） */
    private LocalDateTime upTo(Agent p) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cap = switch (p.getDataMode()) {
            case Agent.MODE_DAILY -> ui.startOfToday();
            case Agent.MODE_MANUAL -> p.getSnapshotAt() != null ? p.getSnapshotAt() : p.getCreatedAt();
            default -> now;
        };
        return cap.isBefore(now) ? cap : now;
    }

    private String describe(Agent s) {
        Deduction d = Deduction.parse(s.getDeductionTiers());
        return " · " + (Boolean.TRUE.equals(s.getCanViewRecords()) ? "可看记录" : "仅看数量") + " · " + ui.dataMode(s.getDataMode())
                + (d.hasTiers() ? " · 扣量：" + d.describe() : "");
    }

    private static String normalize(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    private static String operator(Agent p) {
        return "代理：" + p.getName();
    }
}
