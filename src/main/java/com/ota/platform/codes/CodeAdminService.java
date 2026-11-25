package com.ota.platform.codes;

import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.app.AppService;
import com.ota.platform.audit.AuditService;
import com.ota.platform.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 口令的增删改，后台页面和管理接口共用。口令所有 App 通用 */
@Service
public class CodeAdminService {

    private static final Pattern PREFIX = Pattern.compile("[A-Za-z0-9_-]{0,16}");
    private static final int MAX_BATCH = 1000;

    private final ActivationCodeRepo codeRepo;
    private final AgentRepo agentRepo;
    private final AuditService audit;
    private final com.ota.platform.web.agent.CodeLimits limits;
    private final SecureRandom random = new SecureRandom();

    public CodeAdminService(ActivationCodeRepo codeRepo, AgentRepo agentRepo, AuditService audit, com.ota.platform.web.agent.CodeLimits limits) {
        this.codeRepo = codeRepo;
        this.agentRepo = agentRepo;
        this.audit = audit;
        this.limits = limits;
    }

    public record Spec(String remark, int maxUses, Long agentId, String channel, LocalDateTime expiresAt) {}

    /** 新建一个指定内容的口令 */
    @Transactional
    public ActivationCode create(String code, Spec spec) {
        String value = code == null ? "" : code.trim();
        if (value.isEmpty()) throw ApiException.badRequest("BAD_CODE", "口令不能为空");
        if (value.length() > 64) throw ApiException.badRequest("BAD_CODE", "口令最多 64 位");
        if (codeRepo.existsByCode(value)) throw ApiException.conflict("CODE_EXISTS", "口令已存在：" + value);
        ActivationCode c = build(value, spec);
        codeRepo.save(c);
        audit.log((Long) null, "CODE_CREATE", "创建口令 [" + value + "] 上限:" + (c.getMaxUses() <= 0 ? "不限" : c.getMaxUses())
                + " 代理:" + agentName(c.getAgentId()) + (c.getChannel() != null ? " 渠道:" + c.getChannel() : "")
                + (spec.remark() == null || spec.remark().isBlank() ? "" : " 备注:" + spec.remark().trim()));
        return c;
    }

    /** 批量生成随机口令：前缀 + length 位数字 */
    @Transactional
    public List<ActivationCode> generate(int count, String prefix, int length, Spec spec) {
        if (count < 1 || count > MAX_BATCH) throw ApiException.badRequest("BAD_COUNT", "一次生成 1–" + MAX_BATCH + " 个");
        String p = prefix == null ? "" : prefix.trim();
        if (!PREFIX.matcher(p).matches()) throw ApiException.badRequest("BAD_PREFIX", "前缀只能用字母、数字、_ 和 -，最多 16 位");
        if (length < 4 || length > 16) throw ApiException.badRequest("BAD_LENGTH", "随机部分 4–16 位");
        List<ActivationCode> out = new ArrayList<>();
        int attempts = 0;
        while (out.size() < count) {
            if (++attempts > count * 20) throw ApiException.badRequest("CODE_SPACE_FULL", "可用的口令不够了，增加位数或换个前缀");
            StringBuilder sb = new StringBuilder(p);
            sb.append(1 + random.nextInt(9));
            for (int i = 1; i < length; i++) sb.append(random.nextInt(10));
            String value = sb.toString();
            if (codeRepo.existsByCode(value) || out.stream().anyMatch(c -> c.getCode().equals(value))) continue;
            out.add(build(value, spec));
        }
        codeRepo.saveAll(out);
        audit.log((Long) null, "CODE_CREATE", "批量生成 " + count + " 个口令（" + (p.isEmpty() ? "" : "前缀 " + p + "，") + length + " 位）上限:"
                + (spec.maxUses() <= 0 ? "不限" : spec.maxUses()) + " 代理:" + agentName(spec.agentId()));
        return out;
    }

    /** 按给定的口令批量新建（已存在的跳过），只记一条日志；返回新建的 */
    @Transactional
    public List<ActivationCode> createMany(Collection<String> values, Spec spec, String what) {
        List<ActivationCode> made = new ArrayList<>();
        for (String v : values) {
            if (!codeRepo.existsByCode(v) && made.stream().noneMatch(c -> c.getCode().equals(v))) made.add(build(v, spec));
        }
        codeRepo.saveAll(made);
        if (!made.isEmpty()) {
            audit.log((Long) null, "CODE_CREATE", what + " " + made.size() + " 个口令 上限:" + (spec.maxUses() <= 0 ? "不限" : spec.maxUses())
                    + " 代理:" + agentName(spec.agentId()));
        }
        return made;
    }

    public record Imported(int created, int existing, List<String> invalid) {}

    /** 导入已有的口令：每行一个（也可以用逗号、空格分开），已存在的跳过，同一批里重复的只算一个 */
    @Transactional
    public Imported importCodes(String text, Spec spec) {
        Set<String> values = new LinkedHashSet<>();
        List<String> invalid = new ArrayList<>();
        for (String raw : (text == null ? "" : text).split("[\\s,，;；]+")) {
            String v = raw.trim();
            if (v.isEmpty()) continue;
            if (v.length() > 64) invalid.add(v.substring(0, 16) + "…");
            else values.add(v);
        }
        if (values.isEmpty()) throw ApiException.badRequest("NO_CODES", invalid.isEmpty() ? "没有要导入的口令" : "口令最多 64 位");
        if (values.size() > MAX_BATCH) throw ApiException.badRequest("BAD_COUNT", "一次最多导入 " + MAX_BATCH + " 个");
        List<ActivationCode> made = new ArrayList<>();
        int existing = 0;
        for (String v : values) {
            if (codeRepo.existsByCode(v)) existing++;
            else made.add(build(v, spec));
        }
        codeRepo.saveAll(made);
        audit.log((Long) null, "CODE_CREATE", "导入 " + made.size() + " 个口令" + (existing > 0 ? "（" + existing + " 个已存在，跳过）" : "")
                + " 上限:" + (spec.maxUses() <= 0 ? "不限" : spec.maxUses()) + " 代理:" + agentName(spec.agentId()));
        return new Imported(made.size(), existing, invalid);
    }

    private ActivationCode build(String value, Spec spec) {
        ActivationCode c = new ActivationCode();
        c.setCode(value);
        c.setRemark(spec.remark() == null ? "" : spec.remark().trim());
        c.setMaxUses(Math.max(spec.maxUses(), 0));
        c.setAgentId(owner(spec.agentId()));
        c.setChannel(channel(spec.channel()));
        c.setExpiresAt(spec.expiresAt());
        return c;
    }

    @Transactional
    public ActivationCode update(Long id, String remark, Integer maxUses, Long agentId, boolean changeAgent,
                                 String channel, boolean changeChannel, Boolean enabled, LocalDateTime expiresAt, boolean changeExpiry) {
        ActivationCode c = require(id);
        if (remark != null) c.setRemark(remark.trim());
        if (maxUses != null) c.setMaxUses(Math.max(maxUses, 0));
        if (changeAgent) {
            limits.touch(c.getAgentId());
            c.setAgentId(owner(agentId));
            limits.touch(c.getAgentId());
        }
        if (changeChannel) c.setChannel(channel(channel));
        if (enabled != null) c.setEnabled(enabled);
        if (changeExpiry) c.setExpiresAt(expiresAt);
        codeRepo.save(c);
        audit.log((Long) null, "CODE_UPDATE", "修改口令 [" + c.getCode() + "] 上限:" + (c.getMaxUses() == 0 ? "不限" : c.getMaxUses())
                + " 代理:" + agentName(c.getAgentId()) + (c.getChannel() != null ? " 渠道:" + c.getChannel() : "")
                + (Boolean.FALSE.equals(c.getEnabled()) ? " 停用" : ""));
        return c;
    }

    @Transactional
    public ActivationCode toggle(Long id) {
        ActivationCode c = require(id);
        c.setEnabled(!Boolean.TRUE.equals(c.getEnabled()));
        codeRepo.save(c);
        audit.log((Long) null, "CODE_TOGGLE", "口令 [" + c.getCode() + "] " + (c.getEnabled() ? "启用" : "停用"));
        return c;
    }

    @Transactional
    public ActivationCode delete(Long id) {
        ActivationCode c = require(id);
        audit.log((Long) null, "CODE_DELETE", "删除口令 [" + c.getCode() + "]");
        codeRepo.delete(c);
        limits.touch(c.getAgentId());
        return c;
    }

    /** 批量操作勾选的口令：enable、disable、assign（agentId 为空是取消分配）、delete，返回处理的个数 */
    @Transactional
    public int bulk(Collection<Long> ids, String action, Long agentId) {
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("NO_SELECTION", "先勾选要操作的口令");
        if (ids.size() > MAX_BATCH) throw ApiException.badRequest("TOO_MANY", "一次最多操作 " + MAX_BATCH + " 个口令");
        List<ActivationCode> codes = codeRepo.findAllById(ids);
        if (codes.isEmpty()) throw ApiException.notFound("CODE_NOT_FOUND", "勾选的口令不存在");
        String sample = codes.stream().limit(5).map(ActivationCode::getCode).collect(Collectors.joining("、")) + (codes.size() > 5 ? " 等" : "");
        String what = codes.size() + " 个口令 [" + sample + "]";
        switch (action == null ? "" : action) {
            case "enable", "disable" -> {
                boolean on = "enable".equals(action);
                codes.forEach(c -> c.setEnabled(on));
                codeRepo.saveAll(codes);
                audit.log((Long) null, "CODE_TOGGLE", "批量" + (on ? "启用 " : "停用 ") + what);
            }
            case "assign" -> {
                Long owner = owner(agentId);
                codes.forEach(c -> limits.touch(c.getAgentId()));
                codes.forEach(c -> c.setAgentId(owner));
                codeRepo.saveAll(codes);
                limits.touch(owner);
                audit.log((Long) null, "CODE_UPDATE", "批量分配给 " + agentName(owner) + "：" + what);
            }
            case "delete" -> {
                audit.log((Long) null, "CODE_DELETE", "批量删除 " + what);
                codeRepo.deleteAll(codes);
                codes.forEach(c -> limits.touch(c.getAgentId()));
            }
            default -> throw ApiException.badRequest("BAD_ACTION", "不支持的批量操作");
        }
        return codes.size();
    }

    /** 批量改有效期；expiresAt 为空表示一直有效 */
    public int bulkExpiry(Collection<Long> ids, LocalDateTime expiresAt) {
        List<ActivationCode> codes = picked(ids);
        codes.forEach(c -> c.setExpiresAt(expiresAt));
        codeRepo.saveAll(codes);
        audit.log((Long) null, "CODE_UPDATE", "批量改有效期为 " + (expiresAt == null ? "一直有效" : expiresAt.toLocalDate()) + "：" + described(codes));
        return codes.size();
    }

    /** 批量改每个口令可激活几次（0 不限） */
    public int bulkMaxUses(Collection<Long> ids, Integer maxUses) {
        if (maxUses == null || maxUses < 0 || maxUses > 100_000) throw ApiException.badRequest("BAD_MAX_USES", "可激活次数要在 0 到 100000 之间");
        List<ActivationCode> codes = picked(ids);
        codes.forEach(c -> c.setMaxUses(maxUses));
        codeRepo.saveAll(codes);
        audit.log((Long) null, "CODE_UPDATE", "批量改可激活次数为 " + (maxUses == 0 ? "不限" : maxUses + " 次") + "：" + described(codes));
        return codes.size();
    }

    /** 批量改备注（空就是清空） */
    public int bulkRemark(Collection<Long> ids, String remark) {
        String r = remark == null ? "" : remark.trim();
        if (r.length() > 100) throw ApiException.badRequest("BAD_REMARK", "备注最多 100 个字");
        List<ActivationCode> codes = picked(ids);
        codes.forEach(c -> c.setRemark(r.isEmpty() ? null : r));
        codeRepo.saveAll(codes);
        audit.log((Long) null, "CODE_UPDATE", "批量改备注为「" + r + "」：" + described(codes));
        return codes.size();
    }

    private List<ActivationCode> picked(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) throw ApiException.badRequest("NO_SELECTION", "先勾选要操作的口令");
        if (ids.size() > MAX_BATCH) throw ApiException.badRequest("TOO_MANY", "一次最多操作 " + MAX_BATCH + " 个口令");
        List<ActivationCode> codes = codeRepo.findAllById(ids);
        if (codes.isEmpty()) throw ApiException.notFound("CODE_NOT_FOUND", "勾选的口令不存在");
        return codes;
    }

    private static String described(List<ActivationCode> codes) {
        String sample = codes.stream().limit(5).map(ActivationCode::getCode).collect(Collectors.joining("、")) + (codes.size() > 5 ? " 等" : "");
        return codes.size() + " 个口令 [" + sample + "]";
    }

    public ActivationCode require(Long id) {
        return codeRepo.findById(id == null ? -1L : id)
                .orElseThrow(() -> ApiException.notFound("CODE_NOT_FOUND", "口令不存在"));
    }

    private Long owner(Long agentId) {
        if (agentId == null || agentId <= 0) return null;
        Agent a = agentRepo.findById(agentId).orElseThrow(() -> ApiException.badRequest("AGENT_NOT_FOUND", "代理不存在"));
        if (a.isSub()) throw ApiException.badRequest("SUB_AGENT", a.getName() + " 是二级代理，TA 的口令由上级代理从自己的口令里分");
        return a.getId();
    }

    private static String channel(String channel) {
        if (channel == null || channel.isBlank()) return null;
        String c = channel.trim();
        if (!AppService.isChannelName(c)) throw ApiException.badRequest("BAD_CHANNEL", "渠道名只能用小写字母、数字和 . _ -");
        return c;
    }

    private String agentName(Long agentId) {
        return agentId == null ? "未分配" : agentRepo.findById(agentId).map(Agent::getName).orElse("未分配");
    }
}
