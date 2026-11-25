package com.ota.platform.agent;

import com.ota.platform.audit.AuditService;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.codes.ActivationRecordRepo;
import com.ota.platform.codes.CodeAdminService;
import com.ota.platform.common.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 代理申请口令、审核、删除代理。代理不分 App，口令所有 App 通用。
 * 申请口令按代理的设置走：不能申请、申请后等管理员审核、自动通过；每天能申请的个数有上限，自动通过也受它限制。
 */
@Service
public class AgentService {

    public static final int MAX_PER_REQUEST = 1000;
    public static final int MAX_CUSTOM = 500;
    public static final int MAX_USES_LIMIT = 100_000;
    private static final int CODE_LENGTH = 8;
    /** 代理自己填的口令：字母（含中文）、数字、_ 和 -，2–32 位 */
    private static final java.util.regex.Pattern CUSTOM_CODE = java.util.regex.Pattern.compile("^[\\p{L}\\p{N}_-]{2,32}$");

    private final AgentRepo agents;
    private final CodeRequestRepo requests;
    private final CodeRequestItemRepo items;
    private final ActivationCodeRepo codes;
    private final ActivationRecordRepo records;
    private final CodeAdminService codeAdmin;
    private final AuditService audit;
    private final AgentSettlementRepo settlements;
    private final com.ota.platform.web.agent.CodeLimits limits;
    private final com.ota.platform.notice.NoticeService notices;

    public AgentService(AgentRepo agents, CodeRequestRepo requests, CodeRequestItemRepo items, ActivationCodeRepo codes,
                        ActivationRecordRepo records, CodeAdminService codeAdmin, AuditService audit, AgentSettlementRepo settlements,
                        com.ota.platform.web.agent.CodeLimits limits, com.ota.platform.notice.NoticeService notices) {
        this.notices = notices;
        this.settlements = settlements;
        this.limits = limits;
        this.items = items;
        this.agents = agents;
        this.requests = requests;
        this.codes = codes;
        this.records = records;
        this.codeAdmin = codeAdmin;
        this.audit = audit;
    }

    // ==================== 申请口令 ====================

    /** since 以后申请的个数（待审核的按申请数，已通过的按实际生成数） */
    public long requestedSince(Agent a, LocalDateTime since) {
        return requests.requestedSince(a.getId(), since);
    }

    /** 今天还能申请多少个；不限时返回 -1 */
    public long remaining(Agent a, LocalDateTime startOfToday) {
        int limit = a.getDailyRequestLimit();
        if (limit <= 0) return -1;
        return Math.max(0, limit - requestedSince(a, startOfToday));
    }

    /** rejected：提交时就自动拒绝的口令（已存在或和别的申请重复）。全部重复时 request 是已拒绝的 */
    public record Submitted(CodeRequest request, Approved approved, List<CodeRequestItem> rejected) {}

    /** 把代理填的口令拆开：一行一个，也可以用空格、逗号、顿号隔开；同一次里重复的只算一个 */
    public static List<String> parseCodes(String text) {
        Set<String> out = new LinkedHashSet<>();
        List<String> bad = new ArrayList<>();
        for (String raw : (text == null ? "" : text).split("[\\s,，;；、]+")) {
            String v = raw.trim();
            if (v.isEmpty()) continue;
            if (CUSTOM_CODE.matcher(v).matches()) out.add(v);
            else bad.add(v.length() > 16 ? v.substring(0, 16) + "…" : v);
        }
        if (!bad.isEmpty()) {
            throw ApiException.badRequest("BAD_CODE", "口令只能用字母、数字、_ 和 -，2–32 位，这些不行：" + String.join("、", bad.subList(0, Math.min(5, bad.size())))
                    + (bad.size() > 5 ? " 等 " + bad.size() + " 个" : ""));
        }
        if (out.isEmpty()) throw ApiException.badRequest("NO_CODES", "填上要申请的口令");
        if (out.size() > MAX_CUSTOM) throw ApiException.badRequest("BAD_COUNT", "一次最多申请 " + MAX_CUSTOM + " 个自定义口令");
        return new ArrayList<>(out);
    }

    /** custom=false 随机生成 count 个；custom=true 申请 codesText 里的口令 */
    @Transactional
    public Submitted submit(Agent a, boolean custom, int count, String codesText, int maxUses, String note, LocalDateTime startOfToday) {
        if (!Boolean.TRUE.equals(a.getEnabled())) throw ApiException.badRequest("AGENT_DISABLED", "账号已停用");
        if (!a.canRequest()) throw ApiException.badRequest("REQUEST_OFF", "管理员没有开放申请口令");
        if (maxUses < 0 || maxUses > MAX_USES_LIMIT) throw ApiException.badRequest("BAD_MAX_USES", "每个口令能激活的次数填 0（不限）到 " + MAX_USES_LIMIT);
        String text = note == null ? "" : note.trim();
        if (text.length() > 200) throw ApiException.badRequest("BAD_NOTE", "备注最多 200 字");

        List<String> wanted = List.of();
        Map<String, String> dup = new LinkedHashMap<>();
        if (custom) {
            wanted = parseCodes(codesText);
            Set<String> exist = new HashSet<>();
            for (String c : wanted) if (codes.existsByCode(c)) exist.add(c);
            Set<String> pending = new HashSet<>(items.pendingCodes(wanted));
            for (String c : wanted) {
                if (exist.contains(c)) dup.put(c, "已经有这个口令");
                else if (pending.contains(c)) dup.put(c, "和别的申请重复");
            }
            count = wanted.size() - dup.size();
        } else if (count < 1 || count > MAX_PER_REQUEST) {
            throw ApiException.badRequest("BAD_COUNT", "一次申请 1–" + MAX_PER_REQUEST + " 个");
        }
        long left = remaining(a, startOfToday);
        if (left >= 0 && count > left) {
            throw ApiException.badRequest("DAILY_LIMIT", left == 0 ? "今天的申请额度用完了（每天最多 " + a.getDailyRequestLimit() + " 个），明天再来"
                    : "今天还能申请 " + left + " 个（每天最多 " + a.getDailyRequestLimit() + " 个）");
        }
        CodeRequest r = requests.save(new CodeRequest(a.getId(), Math.max(count, 0), maxUses, text.isEmpty() ? null : text, custom));
        List<CodeRequestItem> rejected = new ArrayList<>();
        if (custom) {
            List<CodeRequestItem> rows = new ArrayList<>();
            for (String c : wanted) {
                String why = dup.get(c);
                CodeRequestItem it = new CodeRequestItem(r.getId(), c, why == null ? CodeRequestItem.PENDING : CodeRequestItem.REJECTED, why);
                rows.add(it);
                if (why != null) rejected.add(it);
            }
            items.saveAll(rows);
        }
        String what = custom ? "自定义口令 " + wanted.size() + " 个" + (dup.isEmpty() ? "" : "（" + dup.size() + " 个重复，自动拒绝）") : count + " 个口令";
        audit.logAs(agentOperator(a), null, "CODE_REQUEST", "代理 " + a.getName() + " 申请" + what
                + "（每个" + uses(maxUses) + "）" + (text.isEmpty() ? "" : "，用途：" + text) + " · 申请 #" + r.getId());
        if (custom && count == 0) {
            r.setStatus(CodeRequest.REJECTED);
            r.setReviewNote("口令都重复了，自动拒绝");
            r.setReviewedBy("系统");
            r.setReviewedAt(LocalDateTime.now());
            r.setAuto(true);
            requests.save(r);
            return new Submitted(r, null, rejected);
        }
        Approved approved = null;
        if (Agent.REQUEST_AUTO.equals(a.getRequestMode())) approved = approve(r.getId(), null, null, null, agentOperator(a), true, null);
        return new Submitted(r, approved, rejected);
    }

    @Transactional
    public CodeRequest cancel(Agent a, Long id) {
        CodeRequest r = requests.findById(id).filter(x -> x.getAgentId().equals(a.getId()))
                .orElseThrow(() -> ApiException.notFound("NO_REQUEST", "没有这条申请"));
        if (!r.isPending()) throw ApiException.badRequest("NOT_PENDING", "这条申请已经处理过了");
        r.setStatus(CodeRequest.CANCELLED);
        r.setReviewedAt(LocalDateTime.now());
        requests.save(r);
        closeItems(r, "代理撤回了申请");
        audit.logAs(agentOperator(a), null, "CODE_REQUEST_CANCEL", "代理 " + a.getName() + " 撤回申请 #" + r.getId());
        return r;
    }

    public record Approved(CodeRequest request, Agent agent, List<ActivationCode> codes, List<String> skipped) {}

    /**
     * 同意申请：生成口令、直接归属这个代理。随机申请：count、maxUses 为 null 时按申请的来。
     * 自定义申请：pick 是同意的那些口令（null 是全部），没选的拒绝；这时候已经有人建了的跳过
     */
    @Transactional
    public Approved approve(Long id, Integer count, Integer maxUses, LocalDateTime expiresAt, String reviewer, boolean auto, Collection<String> pick) {
        CodeRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("NO_REQUEST", "没有这条申请"));
        if (!r.isPending()) throw ApiException.badRequest("NOT_PENDING", "申请 #" + id + " 已经处理过了");
        Agent a = agents.findById(r.getAgentId()).orElseThrow(() -> ApiException.badRequest("AGENT_GONE", "申请 #" + id + " 的代理已经删除"));
        if (!Boolean.TRUE.equals(a.getEnabled())) throw ApiException.badRequest("AGENT_DISABLED", "代理 " + a.getName() + " 已停用，先启用再同意");
        int uses = maxUses == null ? r.getMaxUses() : maxUses;
        if (uses < 0 || uses > MAX_USES_LIMIT) throw ApiException.badRequest("BAD_MAX_USES", "每个口令能激活的次数填 0（不限）到 " + MAX_USES_LIMIT);
        String remark = "代理申请 #" + r.getId() + (r.getNote() == null ? "" : " · " + r.getNote());
        CodeAdminService.Spec spec = new CodeAdminService.Spec(remark.length() > 200 ? remark.substring(0, 200) : remark, uses, a.getId(), null, expiresAt);
        List<ActivationCode> made;
        List<String> skipped = new ArrayList<>();
        if (r.isCustom()) {
            List<CodeRequestItem> pending = items.findByRequestIdOrderByIdAsc(r.getId()).stream().filter(CodeRequestItem::isPending).toList();
            Set<String> chosen = pick == null ? null : new HashSet<>(pick);
            List<String> values = new ArrayList<>();
            for (CodeRequestItem it : pending) {
                if (chosen != null && !chosen.contains(it.getCode())) it.reject("没有同意");
                else if (codes.existsByCode(it.getCode())) {
                    it.reject("已经有这个口令");
                    skipped.add(it.getCode());
                } else values.add(it.getCode());
            }
            made = codeAdmin.createMany(values, spec, "代理申请 #" + r.getId() + " 生成");
            Set<String> createdValues = new HashSet<>();
            made.forEach(c -> createdValues.add(c.getCode()));
            for (CodeRequestItem it : pending) if (it.isPending()) {
                if (createdValues.contains(it.getCode())) it.approve();
                else it.reject("已经有这个口令");
            }
            items.saveAll(pending);
            if (made.isEmpty()) {
                r.setStatus(CodeRequest.REJECTED);
                r.setReviewNote(chosen != null && chosen.isEmpty() ? null : "选的口令都已经有了");
                r.setCodeCount(0);
                r.setReviewedBy(reviewer);
                r.setReviewedAt(LocalDateTime.now());
                r.setAuto(auto);
                requests.save(r);
                audit.logAs(auto ? agentOperator(a) : AuditService.operator(), null, "CODE_REQUEST_REJECT",
                        "代理 " + a.getName() + " 的申请 #" + r.getId() + " 没有可以生成的口令（都已存在或没选）");
                return new Approved(r, a, made, skipped);
            }
        } else {
            int n = count == null ? r.getCount() : count;
            if (n < 1 || n > MAX_PER_REQUEST) throw ApiException.badRequest("BAD_COUNT", "生成个数 1–" + MAX_PER_REQUEST);
            made = codeAdmin.generate(n, "", CODE_LENGTH, spec);
        }
        made.forEach(c -> c.setRequestId(r.getId()));
        codes.saveAll(made);
        r.setStatus(CodeRequest.APPROVED);
        r.setCodeCount(made.size());
        r.setReviewedBy(reviewer);
        r.setReviewedAt(LocalDateTime.now());
        r.setAuto(auto);
        requests.save(r);
        audit.logAs(auto ? agentOperator(a) : AuditService.operator(), null, "CODE_REQUEST_APPROVE",
                (auto ? "自动通过" : "同意") + "代理 " + a.getName() + " 的申请 #" + r.getId() + "，生成 " + made.size() + " 个"
                        + (r.isCustom() ? "自定义" : "") + "口令（每个" + uses(uses) + (expiresAt != null ? "，" + expiresAt.toLocalDate() + " 到期" : "") + "）"
                        + (r.isCustom() && made.size() < r.getCount() ? "，" + (r.getCount() - made.size()) + " 个没同意或已存在" : ""));
        return new Approved(r, a, made, skipped);
    }

    @Transactional
    public CodeRequest reject(Long id, String reason, String reviewer) {
        CodeRequest r = requests.findById(id).orElseThrow(() -> ApiException.notFound("NO_REQUEST", "没有这条申请"));
        if (!r.isPending()) throw ApiException.badRequest("NOT_PENDING", "申请 #" + id + " 已经处理过了");
        String why = reason == null ? "" : reason.trim();
        if (why.length() > 200) throw ApiException.badRequest("BAD_REASON", "原因最多 200 字");
        r.setStatus(CodeRequest.REJECTED);
        r.setReviewNote(why.isEmpty() ? null : why);
        r.setReviewedBy(reviewer);
        r.setReviewedAt(LocalDateTime.now());
        requests.save(r);
        closeItems(r, why.isEmpty() ? "管理员拒绝了这条申请" : why);
        String name = agents.findById(r.getAgentId()).map(Agent::getName).orElse("（已删除）");
        audit.log((Long) null, "CODE_REQUEST_REJECT", "拒绝代理 " + name + " 的申请 #" + r.getId() + "（" + r.getCount() + " 个口令）"
                + (why.isEmpty() ? "" : "，原因：" + why));
        return r;
    }

    private void closeItems(CodeRequest r, String why) {
        if (!r.isCustom()) return;
        List<CodeRequestItem> list = items.findByRequestIdOrderByIdAsc(r.getId());
        list.stream().filter(CodeRequestItem::isPending).forEach(it -> it.reject(why.length() > 100 ? why.substring(0, 100) : why));
        items.saveAll(list);
    }

    /** 自定义申请的口令：申请 ID → 口令 */
    public Map<Long, List<CodeRequestItem>> itemsOf(Collection<Long> requestIds) {
        Map<Long, List<CodeRequestItem>> m = new HashMap<>();
        if (requestIds == null || requestIds.isEmpty()) return m;
        for (CodeRequestItem it : items.findByRequestIdInOrderByIdAsc(requestIds)) m.computeIfAbsent(it.getRequestId(), k -> new ArrayList<>()).add(it);
        return m;
    }

    public Optional<CodeRequest> find(Long id) {
        return id == null ? Optional.empty() : requests.findById(id);
    }

    public long pendingOf(Long agentId) {
        return requests.countByAgentIdAndStatus(agentId, CodeRequest.PENDING);
    }

    /** 待审核的申请来自哪些代理：来自 张三、李四 等 3 个代理 */
    public String pendingAgentsText() {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (CodeRequest r : requests.findByStatusOrderByCreatedAtAsc(CodeRequest.PENDING)) ids.add(r.getAgentId());
        if (ids.isEmpty()) return "";
        List<String> names = new ArrayList<>();
        for (Agent a : agents.findAllById(ids)) names.add(a.getName());
        return "来自 " + String.join("、", names.subList(0, Math.min(3, names.size()))) + (ids.size() > 3 ? " 等 " + ids.size() + " 个代理" : "");
    }

    public long pendingCount() {
        return requests.countByStatus(CodeRequest.PENDING);
    }

    /** 后台列表：待审核的全部（先来的在前），其余按时间倒序取最近 limit 条 */
    public List<CodeRequest> list(String status, int limit) {
        if (CodeRequest.PENDING.equals(status)) return requests.findByStatusOrderByCreatedAtAsc(status);
        if (status == null || status.isBlank() || "ALL".equals(status)) return requests.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit));
        return requests.findByStatusOrderByCreatedAtDesc(status, PageRequest.of(0, limit));
    }

    public List<CodeRequest> ofAgent(Long agentId, int limit) {
        return requests.findByAgentIdOrderByCreatedAtDesc(agentId, PageRequest.of(0, limit));
    }

    public Map<String, Long> counts() {
        Map<String, Long> m = new LinkedHashMap<>();
        for (String s : List.of(CodeRequest.PENDING, CodeRequest.APPROVED, CodeRequest.REJECTED, CodeRequest.CANCELLED)) {
            m.put(s, requests.countByStatus(s));
        }
        m.put("ALL", requests.count());
        return m;
    }

    // ==================== 删除代理 ====================

    /** codes：一级代理是名下的口令；二级代理是收回给上级的口令 */
    public record Deleted(int codes, int records, int requests, int subs) {}

    /**
     * 删除代理。purge：TA 名下的口令和这些口令的激活记录一起删；否则口令改为未分配。TA 的二级代理一起删。
     * 删的是二级代理时，分给 TA 的口令回到上级手里
     */
    @Transactional
    public Deleted delete(Agent a, boolean purge) {
        if (a.isSub()) {
            List<ActivationCode> given = codes.findBySubAgentIdOrderByCreatedAtDesc(a.getId());
            given.forEach(c -> c.setSubAgentId(null));
            codes.saveAll(given);
            settlements.deleteByAgentIds(List.of(a.getId()));
            notices.forget(List.of(a.getId()));
            agents.delete(a);
            limits.touch(a.getParentId());
            return new Deleted(given.size(), 0, 0, 0);
        }
        List<Agent> subs = agents.findByParentIdOrderByCreatedAtDesc(a.getId());
        List<ActivationCode> mine = codes.findByAgentIdOrderByCreatedAtDesc(a.getId());
        int recs = 0;
        if (purge) {
            List<Long> ids = mine.stream().map(ActivationCode::getId).toList();
            for (int i = 0; i < ids.size(); i += 500) recs += records.deleteByCodeIdIn(ids.subList(i, Math.min(ids.size(), i + 500)));
            codes.deleteAll(mine);
        } else {
            mine.forEach(c -> c.setAgentId(null));
            codes.saveAll(mine);
        }
        items.deleteByAgentId(a.getId());
        int reqs = requests.deleteByAgentId(a.getId());
        List<Long> gone = new ArrayList<>(subs.stream().map(Agent::getId).toList());
        gone.add(a.getId());
        settlements.deleteByAgentIds(gone);
        notices.forget(gone);
        agents.deleteAll(subs);
        agents.delete(a);
        limits.touch(a.getId());
        return new Deleted(mine.size(), recs, reqs, subs.size());
    }

    private static String agentOperator(Agent a) {
        return "代理：" + a.getName();
    }

    private static String uses(int maxUses) {
        return maxUses <= 0 ? "不限次数" : "可激活 " + maxUses + " 次";
    }
}
