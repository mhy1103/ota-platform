package com.ota.platform.web.admin;

import com.ota.platform.web.agent.AgentPortal;
import com.ota.platform.web.AdminUi;
import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AdminUserService;
import com.ota.platform.admin.AppPerm;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.agent.AgentService;
import com.ota.platform.agent.CodeRequest;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.common.ApiException;
import com.ota.platform.settings.SettingService;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@Controller
@RequestMapping("/admin/agents")
public class AdminAgentController {

    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_.-]{3,32}$");
    private static final Set<String> MODES = Set.of(Agent.MODE_REALTIME, Agent.MODE_DAILY, Agent.MODE_MANUAL);
    private static final Set<String> REQUEST_MODES = Set.of(Agent.REQUEST_OFF, Agent.REQUEST_REVIEW, Agent.REQUEST_AUTO);
    private static final Set<String> REQUEST_FILTERS = Set.of(CodeRequest.PENDING, CodeRequest.APPROVED, CodeRequest.REJECTED, "ALL");
    private static final int REQUEST_LIST_LIMIT = 300;

    private final AgentRepo agentRepo;
    private final ActivationCodeRepo codeRepo;
    private final AgentPortal portal;
    private final AdminShell shell;
    private final AdminUi ui;
    private final AuditService audit;
    private final PasswordEncoder encoder;
    private final SettingService settings;
    private final AdminUserService adminUsers;
    private final AgentService agents;
    private final AccessService access;
    private final com.ota.platform.codes.ActivationRecordRepo recordRepo;
    private final com.ota.platform.audit.OperationLogRepo logRepo;
    private final com.ota.platform.notice.NoticeService notices;
    private final com.ota.platform.web.agent.Settlement settlement;
    private final ActiveReport active;

    public AdminAgentController(AgentRepo agentRepo, ActivationCodeRepo codeRepo, AgentPortal portal, AdminShell shell,
                                AdminUi ui, AuditService audit, PasswordEncoder encoder, SettingService settings, AdminUserService adminUsers,
                                AgentService agents, AccessService access, com.ota.platform.codes.ActivationRecordRepo recordRepo,
                                com.ota.platform.audit.OperationLogRepo logRepo, com.ota.platform.notice.NoticeService notices,
                                com.ota.platform.web.agent.Settlement settlement, ActiveReport active) {
        this.settlement = settlement;
        this.active = active;
        this.notices = notices;
        this.recordRepo = recordRepo;
        this.logRepo = logRepo;
        this.agentRepo = agentRepo;
        this.codeRepo = codeRepo;
        this.portal = portal;
        this.shell = shell;
        this.ui = ui;
        this.audit = audit;
        this.encoder = encoder;
        this.settings = settings;
        this.adminUsers = adminUsers;
        this.agents = agents;
        this.access = access;
    }

    /** 申请列表的一行 */
    public record RequestView(Long id, Long agentId, String agentName, boolean agentEnabled, int count, int maxUses,
                              String note, String status, String statusLabel, Integer codeCount, String reviewNote, String reviewedBy,
                              LocalDateTime reviewedAt, LocalDateTime createdAt, boolean auto, boolean canApprove,
                              boolean custom, List<ItemView> items, int duplicates, String itemsJson) {}

    /** 自定义申请里的一个口令 */
    public record ItemView(String code, String status, String reason) {}

    @GetMapping
    public String list(@RequestParam(defaultValue = CodeRequest.PENDING) String req, Model model, HttpSession session) {
        shell.populate(model, session);
        List<AgentPortal.AgentRow> rows = portal.adminRows();
        model.addAttribute("rows", rows);
        model.addAttribute("enabledCount", rows.stream().filter(r -> Boolean.TRUE.equals(r.getAgent().getEnabled())).count());
        model.addAttribute("manualCount", rows.stream().filter(r -> Agent.MODE_MANUAL.equals(r.getAgent().getDataMode())).count());
        long today = 0, yesterday = 0, week = 0, prevWeek = 0, month = 0, total = 0, codes = 0, usable = 0, active = 0;
        for (AgentPortal.AgentRow r : rows) {
            today += r.getToday();
            yesterday += r.getYesterday();
            week += r.getWeek();
            prevWeek += r.getPrevWeek();
            month += r.getMonth();
            total += r.getTotal();
            codes += r.getCodeCount();
            usable += r.getUsable();
            if (r.getToday() > 0) active++;
        }
        model.addAttribute("sums", new Sums(active, today, yesterday, week, month, total, codes, usable,
                AdminController.delta(today, yesterday), AdminController.delta(week, prevWeek)));

        String filter = REQUEST_FILTERS.contains(req) ? req : CodeRequest.PENDING;
        Map<Long, Agent> agentById = new HashMap<>();
        for (AgentPortal.AgentRow r : rows) agentById.put(r.getAgent().getId(), r.getAgent());
        CurrentAdmin me = access.current();
        List<CodeRequest> list = agents.list(filter, REQUEST_LIST_LIMIT);
        Map<Long, List<com.ota.platform.agent.CodeRequestItem>> itemsByRequest = agents.itemsOf(list.stream().filter(CodeRequest::isCustom).map(CodeRequest::getId).toList());
        List<RequestView> requests = new ArrayList<>();
        for (CodeRequest r : list) {
            Agent a = agentById.get(r.getAgentId());
            List<ItemView> its = itemsByRequest.getOrDefault(r.getId(), List.of()).stream()
                    .map(i -> new ItemView(i.getCode(), i.getStatus(), i.getReason())).toList();
            int dups = (int) its.stream().filter(i -> "REJECTED".equals(i.status()) && i.reason() != null && (i.reason().contains("这个口令") || i.reason().contains("重复"))).count();
            requests.add(new RequestView(r.getId(), r.getAgentId(), a == null ? "（已删除）" : a.getName(), a != null && Boolean.TRUE.equals(a.getEnabled()),
                    r.getCount(), r.getMaxUses(), r.getNote(), r.getStatus(), statusLabel(r),
                    r.getCodeCount(), r.getReviewNote(), r.getReviewedBy(), r.getReviewedAt(), r.getCreatedAt(), r.isAuto(),
                    me != null && me.canCodes(), r.isCustom(), its, dups, r.isCustom() ? itemsJson(its) : null));
        }
        model.addAttribute("requests", requests);
        model.addAttribute("reqFilter", filter);
        model.addAttribute("requestCounts", agents.counts());
        model.addAttribute("notices", notices.adminList());
        Map<String, String> names = new HashMap<>();
        for (com.ota.platform.admin.AdminUser u : adminUsers.all()) names.put(u.getUsername(), u.getName());
        model.addAttribute("operatorNames", names);
        return "admin/agents";
    }

    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> export() {
        List<AgentPortal.AgentRow> rows = portal.adminRows();
        com.ota.platform.web.Csv csv = new com.ota.platform.web.Csv().row("代理", "账号", "状态", "口令", "可用口令",
                "今天激活", "近 7 天", "近 30 天", "累计激活", "最近登录", "创建时间", "备注");
        for (AgentPortal.AgentRow r : rows) {
            Agent a = r.getAgent();
            csv.row(a.getName(), a.getUsername(), Boolean.TRUE.equals(a.getEnabled()) ? "启用" : "停用", r.getCodeCount(), r.getUsable(),
                    r.getToday(), r.getWeek(), r.getMonth(), r.getTotal(), ui.full(a.getLastLoginAt()), ui.full(a.getCreatedAt()),
                    a.getRemark() == null ? "" : a.getRemark());
        }
        audit.log("AGENTS_EXPORT", "导出 " + rows.size() + " 个代理");
        return csv.download("代理-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv");
    }

    /** 代理名下口令的激活记录，所有 App，不扣量 */
    @GetMapping("/{id}/export")
    public org.springframework.http.ResponseEntity<byte[]> exportRecords(@PathVariable Long id) {
        Agent a = agentRepo.findById(id).orElse(null);
        if (a == null) return org.springframework.http.ResponseEntity.status(404).build();
        Map<Long, String> appNames = new HashMap<>();
        for (OtaApp o : portal.allApps()) appNames.put(o.getId(), o.getName());
        List<Long> codeIds = codeRepo.findByAgentIdOrderByCreatedAtDesc(id).stream().map(ActivationCode::getId).toList();
        List<com.ota.platform.codes.ActivationRecord> rows = codeIds.isEmpty() ? List.of()
                : recordRepo.findAll((root, cq, cb) -> root.get("codeId").in(codeIds),
                        org.springframework.data.domain.PageRequest.of(0, 100_000,
                                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "activatedAt"))).getContent();
        com.ota.platform.web.Csv csv = new com.ota.platform.web.Csv().row("提交时间", "App", "口令", "设备 ID", "状态", "确认时间");
        for (com.ota.platform.codes.ActivationRecord r : rows) {
            csv.row(ui.full(r.getActivatedAt()), appNames.getOrDefault(r.getAppId(), ""), r.getCodeText(), r.getDeviceId(),
                    Boolean.TRUE.equals(r.getConfirmed()) ? "已确认" : "待确认", ui.full(r.getConfirmedAt()));
        }
        audit.log("AGENT_RECORDS_EXPORT", "导出代理 " + a.getName() + " 的 " + rows.size() + " 条激活记录");
        return csv.download(a.getName() + "-激活记录-" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv");
    }

    /** 代理列表上面的合计 */
    public record Sums(long active, long today, long yesterday, long week, long month, long total, long codes, long usable,
                       AdminController.Delta todayDelta, AdminController.Delta weekDelta) {}

    /** 代理详情：按月一行 */
    public record Month(String label, long submitted, long confirmed, long rate) {}
    /** 代理详情：口令用在了哪些 App，每个 App 一行 */
    public record AppStat(Long id, String name, long confirmed, long share, long today) {}
    /** 代理详情：口令一行 */
    /** seen：代理看到的次数（扣量后的，次数上限按它算），没扣过量时为空 */
    public record CodeStat(Long id, String code, String remark, long confirmed, int maxUses, int usedCount, String status, Integer seen) {}
    /** 代理详情：最近的激活 */
    public record Recent(LocalDateTime at, String code, String appName, boolean confirmed, String device, boolean fast, boolean dup) {}
    /** 代理详情：申请一行 */
    public record ReqLine(Long id, LocalDateTime at, boolean custom, int count, String status, String statusLabel,
                          Integer codeCount, String reviewNote) {}

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model, HttpSession session) {
        shell.populate(model, session);
        Agent a = agentRepo.findById(id).orElse(null);
        if (a == null) return "redirect:/admin/agents";
        if (a.isSub()) return "redirect:/admin/agents/" + a.getParentId() + "#subs";
        AgentPortal.AgentRow row = portal.adminRows().stream().filter(r -> r.getAgent().getId().equals(id)).findFirst().orElse(null);
        if (row == null) return "redirect:/admin/agents";
        model.addAttribute("subs", portal.subRows(a, true));
        Map<Long, String> appNames = new HashMap<>();
        for (OtaApp o : portal.allApps()) appNames.put(o.getId(), o.getName());
        model.addAttribute("agent", a);
        model.addAttribute("row", row);
        model.addAttribute("todayDelta", AdminController.delta(row.getToday(), row.getYesterday()));
        model.addAttribute("weekDelta", AdminController.delta(row.getWeek(), row.getPrevWeek()));

        List<ActivationCode> codes = codeRepo.findAll().stream().filter(c -> id.equals(c.getAgentId())).toList();
        List<Long> codeIds = codes.stream().map(ActivationCode::getId).toList();
        LocalDate today = ui.today();
        LocalDate monthStart = today.withDayOfMonth(1).minusMonths(5);
        long[] daily = new long[30];
        Map<String, long[]> perMonth = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) perMonth.put(monthStart.plusMonths(i).toString().substring(0, 7), new long[2]);
        Map<Long, long[]> perApp = new HashMap<>();
        if (!codeIds.isEmpty()) {
            LocalDateTime since = monthStart.atStartOfDay().minusDays(1);
            for (Object[] r : recordRepo.activityForCodes(codeIds, since)) {
                LocalDateTime at = (LocalDateTime) r[0], conf = (LocalDateTime) r[1];
                boolean ok = Boolean.TRUE.equals(r[2]);
                if (at != null) {
                    long[] m = perMonth.get(ui.localDate(at).toString().substring(0, 7));
                    if (m != null) m[0]++;
                }
                if (ok && conf != null) {
                    LocalDate d = ui.localDate(conf);
                    long[] m = perMonth.get(d.toString().substring(0, 7));
                    if (m != null) m[1]++;
                    long back = java.time.temporal.ChronoUnit.DAYS.between(d, today);
                    if (back >= 0 && back < 30) daily[29 - (int) back]++;
                    if (d.equals(today)) perApp.computeIfAbsent((Long) r[3], k -> new long[1])[0]++;
                }
            }
        }
        long max = Arrays.stream(daily).max().orElse(0);
        java.time.format.DateTimeFormatter md = java.time.format.DateTimeFormatter.ofPattern("MM-dd");
        List<AdminController.Day> bars = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            LocalDate d = today.minusDays(29 - i);
            boolean isToday = i == 29;
            String label = isToday ? "今天" : (29 - i) % 5 == 0 ? md.format(d) : "";
            bars.add(new AdminController.Day(label, isToday ? "今天" : md.format(d), daily[i],
                    max == 0 ? 2 : (int) Math.max(2, Math.round(daily[i] * 140.0 / max)), isToday));
        }
        model.addAttribute("bars", bars);
        List<Month> months = new ArrayList<>();
        perMonth.forEach((k, v) -> months.add(new Month(k.replace('-', '.'), v[0], v[1], com.ota.platform.common.Percent.of(v[1], v[0]))));
        Collections.reverse(months);
        model.addAttribute("months", months);

        Map<Long, Long> confirmedByCode = new HashMap<>();
        for (Object[] r : recordRepo.countConfirmedGroupByCode()) confirmedByCode.put((Long) r[0], (Long) r[1]);
        LocalDateTime now = LocalDateTime.now();
        List<CodeStat> codeStats = new ArrayList<>();
        for (ActivationCode c : codes) {
            boolean expired = c.getExpiresAt() != null && !c.getExpiresAt().isAfter(now);
            boolean used = c.getMaxUses() != null && c.getMaxUses() > 0 && c.limitUsed() >= c.getMaxUses();
            String status = !Boolean.TRUE.equals(c.getEnabled()) ? "disabled" : expired ? "expired" : used ? "exhausted" : "usable";
            codeStats.add(new CodeStat(c.getId(), c.getCode(), c.getRemark(), confirmedByCode.getOrDefault(c.getId(), 0L),
                    c.getMaxUses() == null ? 0 : c.getMaxUses(), c.getUsedCount() == null ? 0 : c.getUsedCount(), status, c.getSeenCount()));
        }
        codeStats.sort(Comparator.comparingLong(CodeStat::confirmed).reversed().thenComparing(CodeStat::code));
        model.addAttribute("topCodes", codeStats.subList(0, Math.min(8, codeStats.size())));
        Map<Long, Long> appConfirmed = new HashMap<>();
        if (!codeIds.isEmpty()) for (Object[] r : recordRepo.confirmedByAppForCodes(codeIds)) appConfirmed.put((Long) r[0], (Long) r[1]);
        long confirmedAll = appConfirmed.values().stream().mapToLong(Long::longValue).sum();
        List<AppStat> appStats = new ArrayList<>();
        appConfirmed.forEach((appId, n) -> appStats.add(new AppStat(appId, appNames.getOrDefault(appId, "（已删除）"), n,
                com.ota.platform.common.Percent.of(n, confirmedAll), perApp.getOrDefault(appId, new long[1])[0])));
        appStats.sort(Comparator.comparingLong(AppStat::confirmed).reversed());
        model.addAttribute("appStats", appStats);
        model.addAttribute("recent", codeIds.isEmpty() ? List.of() : recordRepo.findTop12ByCodeIdInOrderByActivatedAtDesc(codeIds).stream()
                .map(r -> new Recent(r.getActivatedAt(), r.getCodeText(), appNames.getOrDefault(r.getAppId(), "（已删除）"),
                        Boolean.TRUE.equals(r.getConfirmed()), ui.maskDevice(r.getDeviceId()), r.getFastMs() != null, r.getDupOf() != null)).toList());
        model.addAttribute("reqs", agents.ofAgent(id, 10).stream().map(r -> new ReqLine(r.getId(), r.getCreatedAt(),
                r.isCustom(), r.getCount(), r.getStatus(), statusLabel(r), r.getCodeCount(), r.getReviewNote())).toList());
        model.addAttribute("pendingReqs", agents.pendingOf(id));
        model.addAttribute("logs", logRepo.findTop12ByOperatorOrderByCreatedAtDesc(a.getUsername()));
        return "admin/agent-detail";
    }

    /** 日活与留存的时间段按钮 */
    public record RangeOpt(String label, String href, boolean on) {}

    private static List<RangeOpt> rangeOpts(String base, String extra, int range) {
        return ActiveReport.RANGES.stream().map(n -> new RangeOpt("近 " + n + " 天", base + "?" + extra + "days=" + n, n == range)).toList();
    }

    private static int range(int days) {
        return ActiveReport.RANGES.contains(days) ? days : 30;
    }

    /** 全部代理激活的设备的日活与留存，下面按代理列 */
    @GetMapping("/active")
    public String activeAll(@RequestParam(defaultValue = "30") int days, Model model, HttpSession session) {
        shell.populate(model, session);
        int range = range(days);
        List<ActivationCode> codes = codeRepo.findAll().stream().filter(c -> c.getAgentId() != null).toList();
        ActiveReport.Data data = active.fill(model, new ActiveReport.Scope("all", codes), range);
        Map<Object, ActiveReport.Row> names = new LinkedHashMap<>();
        for (Agent a : agentRepo.findByParentIdIsNullOrderByCreatedAtDesc()) {
            names.put(a.getId(), ActiveReport.name(a.getName(), a.getUsername(), "/admin/agents/" + a.getId() + "/active?days=" + range,
                    !Boolean.TRUE.equals(a.getEnabled())));
        }
        model.addAttribute("actKind", "all");
        model.addAttribute("actTitle", "全部代理");
        model.addAttribute("actBack", "/admin/agents");
        model.addAttribute("actBackLabel", "代理管理");
        model.addAttribute("actRangeOpts", rangeOpts("/admin/agents/active", "", range));
        model.addAttribute("actAgentRows", active.rows(data, ActivationCode::getAgentId, names));
        return "admin/agent-active";
    }

    /** 一个代理激活的设备的日活与留存；sub 只看 TA 的一个二级代理，code 只看一个口令 */
    @GetMapping("/{id}/active")
    public String active(@PathVariable Long id, @RequestParam(required = false) Long sub, @RequestParam(required = false) Long code,
                         @RequestParam(defaultValue = "30") int days, Model model, HttpSession session) {
        shell.populate(model, session);
        Agent a = agentRepo.findById(id).orElse(null);
        if (a == null) return "redirect:/admin/agents/active";
        if (a.isSub()) return "redirect:/admin/agents/" + a.getParentId() + "/active?sub=" + a.getId();
        int range = range(days);
        String base = "/admin/agents/" + id + "/active";
        List<ActivationCode> mine = codeRepo.findByAgentIdOrderByCreatedAtDesc(id);
        Map<Long, Agent> subs = new LinkedHashMap<>();
        for (Agent s : agentRepo.findByParentIdOrderByCreatedAtDesc(id)) subs.put(s.getId(), s);
        ActivationCode one = code == null ? null : mine.stream().filter(c -> c.getId().equals(code)).findFirst().orElse(null);
        Agent s = one == null && sub != null ? subs.get(sub) : null;
        List<ActivationCode> scoped = one != null ? List.of(one) : s != null ? mine.stream().filter(c -> s.getId().equals(c.getSubAgentId())).toList() : mine;
        String kind = one != null ? "code" : s != null ? "sub" : "agent";
        ActiveReport.Data data = active.fill(model, new ActiveReport.Scope(kind, scoped), range);

        if (!"code".equals(kind)) {
            Map<Object, ActiveReport.Row> codeNames = new LinkedHashMap<>();
            for (ActivationCode c : scoped) {
                Agent to = c.getSubAgentId() == null ? null : subs.get(c.getSubAgentId());
                String note = s == null && to != null ? "分给 " + to.getName() : c.getRemark() == null ? "" : c.getRemark();
                codeNames.put(c.getId(), ActiveReport.name(c.getCode(), note, base + "?code=" + c.getId() + "&days=" + range,
                        !Boolean.TRUE.equals(c.getEnabled())));
            }
            model.addAttribute("actCodeRows", active.rows(data, ActivationCode::getId, codeNames));
        }
        if ("agent".equals(kind) && !subs.isEmpty()) {
            Map<Object, ActiveReport.Row> subNames = new LinkedHashMap<>();
            for (Agent x : subs.values()) {
                subNames.put(x.getId(), ActiveReport.name(x.getName(), x.getUsername(), base + "?sub=" + x.getId() + "&days=" + range,
                        !Boolean.TRUE.equals(x.getEnabled())));
            }
            subNames.put(0L, ActiveReport.name("自己手里的口令", "没分给二级代理的", null, false));
            model.addAttribute("actSubRows", active.rows(data, c -> c.getSubAgentId() == null ? 0L : c.getSubAgentId(), subNames));
        }
        model.addAttribute("actKind", kind);
        model.addAttribute("actTitle", a.getName());
        model.addAttribute("actScope", one != null ? "口令 " + one.getCode() : s != null ? "二级代理 " + s.getName() : null);
        model.addAttribute("actAll", base + "?days=" + range);
        model.addAttribute("actBack", one != null || s != null ? base + "?days=" + range : "/admin/agents/" + id);
        model.addAttribute("actBackLabel", one != null || s != null ? a.getName() : "代理详情");
        model.addAttribute("actRangeOpts", rangeOpts(base, one != null ? "code=" + one.getId() + "&" : s != null ? "sub=" + s.getId() + "&" : "", range));
        model.addAttribute("actRetHref", one != null ? null : "/admin/agents/" + (s != null ? s.getId() : id) + "/preview/retention");
        return "admin/agent-active";
    }

    @PostMapping("/create")
    public String create(@RequestParam String name, @RequestParam String username, @RequestParam String password,
                         @RequestParam(defaultValue = "false") boolean canViewRecords,
                         @RequestParam(defaultValue = Agent.MODE_REALTIME) String dataMode,
                         @RequestParam(defaultValue = "") String remark,
                         @RequestParam(required = false) String[] tierMin,
                         @RequestParam(required = false) String[] tierMax,
                         @RequestParam(required = false) String[] tierRatio,
                         @RequestParam(defaultValue = Agent.REQUEST_OFF) String requestMode,
                         @RequestParam(required = false) String dailyLimit,
                         @RequestParam(defaultValue = "false") boolean subAgents,
                         @RequestParam(required = false) String subLimit,
                         RedirectAttributes ra) {
        String login = normalize(username);
        String error = validate(null, name, login, password, true, dataMode);
        if (error == null) error = validateRequest(requestMode, dailyLimit);
        if (error == null) error = validateSubs(subLimit);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/admin/agents";
        }
        Agent a = new Agent();
        apply(a, name, login, canViewRecords, dataMode, remark);
        applyRequest(a, requestMode, dailyLimit);
        applySubs(a, subAgents, subLimit);
        a.setDeductionTiers(buildTiersJson(tierMin, tierMax, tierRatio));
        a.setPasswordHash(encoder.encode(password));
        agentRepo.save(a);
        settlement.created(a);
        audit.log("AGENT_CREATE", "新建代理 " + a.getName() + "（" + a.getUsername() + "）" + describe(a));
        ra.addFlashAttribute("msg", "已创建代理 " + a.getName() + "，登录账号 " + a.getUsername());
        return "redirect:/admin/agents";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable Long id,
                         @RequestParam String name, @RequestParam String username,
                         @RequestParam(defaultValue = "") String password,
                         @RequestParam(defaultValue = "false") boolean canViewRecords,
                         @RequestParam(defaultValue = Agent.MODE_REALTIME) String dataMode,
                         @RequestParam(defaultValue = "") String remark,
                         @RequestParam(required = false) String[] tierMin,
                         @RequestParam(required = false) String[] tierMax,
                         @RequestParam(required = false) String[] tierRatio,
                         @RequestParam(defaultValue = Agent.REQUEST_OFF) String requestMode,
                         @RequestParam(required = false) String dailyLimit,
                         @RequestParam(defaultValue = "false") boolean subAgents,
                         @RequestParam(required = false) String subLimit,
                         @RequestParam(defaultValue = "") String back,
                         RedirectAttributes ra) {
        Agent a = agentRepo.findById(id).orElse(null);
        if (a == null) {
            ra.addFlashAttribute("error", "代理不存在");
            return "redirect:/admin/agents";
        }
        if (a.isSub()) {
            ra.addFlashAttribute("error", a.getName() + " 是二级代理，设置由 TA 的上级代理在代理后台改");
            return "redirect:/admin/agents/" + a.getParentId() + "#subs";
        }
        String login = normalize(username);
        String error = validate(a, name, login, password, false, dataMode);
        if (error == null) error = validateRequest(requestMode, dailyLimit);
        if (error == null) error = validateSubs(subLimit);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/admin/agents";
        }
        boolean hadSubs = a.canManageSubs();
        String tiersBefore = a.getDeductionTiers();
        apply(a, name, login, canViewRecords, dataMode, remark);
        applyRequest(a, requestMode, dailyLimit);
        applySubs(a, subAgents, subLimit);
        a.setDeductionTiers(buildTiersJson(tierMin, tierMax, tierRatio));
        boolean reset = !password.isEmpty();
        if (reset) a.setPasswordHash(encoder.encode(password));
        agentRepo.save(a);
        boolean tiersChanged = settlement.tiersChanged(a, tiersBefore);
        if (!tiersChanged) settlement.settle(a);
        List<Agent> subs = agentRepo.findByParentIdOrderByCreatedAtDesc(a.getId());
        // 上级看不了设备记录了，下面也不能再看
        if (!canViewRecords) {
            List<Agent> viewing = subs.stream().filter(s -> Boolean.TRUE.equals(s.getCanViewRecords())).toList();
            viewing.forEach(s -> s.setCanViewRecords(false));
            agentRepo.saveAll(viewing);
        }
        audit.log("AGENT_UPDATE", "修改代理 " + a.getName() + "（" + a.getUsername() + "）" + describe(a)
                + (reset ? " · 重置密码" : ""));
        String subsNote = hadSubs && !a.canManageSubs() && !subs.isEmpty()
                ? "；关掉了二级代理，TA 的 " + subs.size() + " 个二级代理暂时不能登录，口令和数据都还在，重新打开就恢复" : "";
        String tiersNote = tiersChanged ? "；新的扣量从现在起算，之前的激活还按原来的比例，已经扣过的不会变" : "";
        ra.addFlashAttribute("msg", "已保存代理 " + a.getName() + (reset ? "，密码已重置" : "") + tiersNote + subsNote);
        return "detail".equals(back) ? "redirect:/admin/agents/" + id : "redirect:/admin/agents";
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id, @RequestParam(defaultValue = "") String back, RedirectAttributes ra) {
        Agent a = agentRepo.findById(id).orElse(null);
        if (a == null) return "redirect:/admin/agents";
        a.setEnabled(!Boolean.TRUE.equals(a.getEnabled()));
        agentRepo.save(a);
        String state = a.getEnabled() ? "启用" : "停用";
        String who = a.isSub() ? "二级代理 " : "代理 ";
        audit.log("AGENT_TOGGLE", who + a.getName() + " " + state);
        long subs = a.isSub() ? 0 : agentRepo.countByParentId(a.getId());
        ra.addFlashAttribute("msg", who + a.getName() + " 已" + state
                + (a.getEnabled() ? "" : "，将无法登录" + (subs > 0 ? "，TA 的 " + subs + " 个二级代理也登录不了" : "")));
        // 二级代理没有自己的详情页，直接回上级的
        if (a.isSub()) return "redirect:/admin/agents/" + a.getParentId() + "#subs";
        return "detail".equals(back) ? "redirect:/admin/agents/" + id : "redirect:/admin/agents";
    }

    /** 手动更新模式：把代理能看到的数据推进到现在 */
    @PostMapping("/{id}/refresh")
    public String refresh(@PathVariable Long id, RedirectAttributes ra) {
        agentRepo.findById(id).ifPresent(a -> {
            if (a.isSub()) {
                ra.addFlashAttribute("error", a.getName() + " 是二级代理，数据由 TA 的上级代理更新");
                return;
            }
            if (!Agent.MODE_MANUAL.equals(a.getDataMode())) {
                ra.addFlashAttribute("error", a.getName() + " 不是手动更新模式，无需更新");
                return;
            }
            a.setSnapshotAt(LocalDateTime.now());
            agentRepo.save(a);
            settlement.settle(a);
            audit.log("AGENT_REFRESH", "更新代理 " + a.getName() + " 的数据");
            ra.addFlashAttribute("msg", "已更新 " + a.getName() + " 的数据，截至 " + ui.dts(a.getSnapshotAt()));
        });
        return "redirect:/admin/agents";
    }

    @PostMapping("/refresh-all")
    public String refreshAll(RedirectAttributes ra) {
        LocalDateTime now = LocalDateTime.now();
        List<Agent> manual = agentRepo.findByParentIdIsNullOrderByCreatedAtDesc().stream()
                .filter(a -> Agent.MODE_MANUAL.equals(a.getDataMode())).toList();
        manual.forEach(a -> a.setSnapshotAt(now));
        agentRepo.saveAll(manual);
        if (!manual.isEmpty()) settlement.settleAll();
        if (!manual.isEmpty()) audit.log("AGENT_REFRESH", "更新全部手动代理的数据（" + manual.size() + " 个）");
        ra.addFlashAttribute("msg", "已更新 " + manual.size() + " 个手动更新代理的数据");
        return "redirect:/admin/agents";
    }

    /** 删除代理。purge=true 时 TA 名下的口令、激活记录一起删（要输自己的登录密码）；否则口令改为未分配 */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean purge,
                         @RequestParam(defaultValue = "") String password, RedirectAttributes ra) {
        Agent a = agentRepo.findById(id).orElse(null);
        if (a == null) {
            ra.addFlashAttribute("error", "代理不存在");
            return "redirect:/admin/agents";
        }
        if (a.isSub()) {
            Agent p = agentRepo.findById(a.getParentId()).orElse(null);
            AgentService.Deleted d = agents.delete(a, false);
            String back = d.codes() > 0 ? "，TA 的 " + d.codes() + " 个口令收回给 " + (p == null ? "上级" : p.getName()) : "";
            audit.log("AGENT_DELETE", "删除二级代理 " + a.getName() + "（" + a.getUsername() + "）" + back);
            ra.addFlashAttribute("msg", "已删除二级代理 " + a.getName() + back);
            return p == null ? "redirect:/admin/agents" : "redirect:/admin/agents/" + p.getId() + "#subs";
        }
        try {
            if (purge) checkPurge(List.of(a), password);
            AgentService.Deleted d = agents.delete(a, purge);
            audit.log("AGENT_DELETE", "删除代理 " + a.getName() + "（" + a.getUsername() + "），" + deletedText(d, purge));
            ra.addFlashAttribute("msg", "已删除代理 " + a.getName() + (d.codes() == 0 && d.subs() == 0 ? "" : "，" + deletedText(d, purge)));
        } catch (ApiException e) {
            ra.addFlashAttribute("error", "没有删除：" + e.getMessage());
        }
        return "redirect:/admin/agents";
    }

    /** 批量：启用、停用、申请口令设置、删除 */
    @PostMapping("/bulk")
    public String bulk(@RequestParam(required = false) List<Long> ids, @RequestParam(defaultValue = "") String action,
                       @RequestParam(defaultValue = Agent.REQUEST_REVIEW) String requestMode,
                       @RequestParam(required = false) String dailyLimit,
                       @RequestParam(defaultValue = "false") boolean purge, @RequestParam(defaultValue = "") String password,
                       RedirectAttributes ra) {
        List<Agent> picked = ids == null ? List.of() : agentRepo.findAllById(ids);
        if (picked.isEmpty()) {
            ra.addFlashAttribute("error", "先勾选要操作的代理");
            return "redirect:/admin/agents";
        }
        String names = picked.stream().map(Agent::getName).limit(5).reduce((x, y) -> x + "、" + y).orElse("")
                + (picked.size() > 5 ? " 等 " + picked.size() + " 个" : "");
        try {
            switch (action) {
                case "enable", "disable" -> {
                    boolean on = action.equals("enable");
                    picked.forEach(a -> a.setEnabled(on));
                    agentRepo.saveAll(picked);
                    audit.log("AGENT_TOGGLE", "批量" + (on ? "启用" : "停用") + "代理：" + names);
                    ra.addFlashAttribute("msg", "已" + (on ? "启用 " : "停用 ") + picked.size() + " 个代理" + (on ? "" : "，TA 们将无法登录"));
                }
                case "request" -> {
                    String error = validateRequest(requestMode, dailyLimit);
                    if (error != null) throw ApiException.badRequest("BAD_REQUEST_MODE", error);
                    picked.forEach(a -> applyRequest(a, requestMode, dailyLimit));
                    agentRepo.saveAll(picked);
                    String desc = requestDesc(picked.get(0));
                    audit.log("AGENT_UPDATE", "批量设置申请口令：" + names + " · " + desc);
                    ra.addFlashAttribute("msg", "已把 " + picked.size() + " 个代理的申请口令设为：" + desc);
                }
                case "delete" -> {
                    if (purge) checkPurge(picked, password);
                    int codes = 0, records = 0, subs = 0;
                    for (Agent a : picked) {
                        AgentService.Deleted d = agents.delete(a, purge);
                        codes += d.codes();
                        records += d.records();
                        subs += d.subs();
                    }
                    AgentService.Deleted total = new AgentService.Deleted(codes, records, 0, subs);
                    audit.log("AGENT_DELETE", "批量删除代理：" + names + "，" + deletedText(total, purge));
                    ra.addFlashAttribute("msg", "已删除 " + picked.size() + " 个代理" + (codes == 0 ? "" : "，" + deletedText(total, purge)));
                }
                default -> throw ApiException.badRequest("BAD_ACTION", "选一个批量操作");
            }
        } catch (ApiException e) {
            ra.addFlashAttribute("error", "没有执行：" + e.getMessage());
        }
        return "redirect:/admin/agents";
    }

    // ===== 口令申请 =====

    /** 同意申请。自定义口令的申请：pickCodes=true 时只同意 codes 里勾的，没勾的拒绝 */
    @PostMapping("/requests/{id}/approve")
    public String approve(@PathVariable Long id, @RequestParam(required = false) Integer count,
                          @RequestParam(required = false) Integer maxUses, @RequestParam(defaultValue = "") String expires,
                          @RequestParam(defaultValue = "false") boolean pickCodes, @RequestParam(required = false) List<String> codes,
                          @RequestParam(defaultValue = CodeRequest.PENDING) String back, RedirectAttributes ra) {
        try {
            AgentService.Approved r = agents.approve(requireCodes(id).getId(), count, maxUses, endOfDay(expires), reviewer(), false,
                    pickCodes ? (codes == null ? List.of() : codes) : null);
            if (r.codes().isEmpty()) {
                ra.addFlashAttribute("error", "申请 #" + id + " 没有生成口令：" + (r.skipped().isEmpty() ? "一个都没勾，已按拒绝处理" : "勾的口令都已经有了，已按拒绝处理"));
            } else {
                ra.addFlashAttribute("msg", "已同意 " + r.agent().getName() + " 的申请，生成 " + r.codes().size()
                        + " 个口令，TA 在代理后台就能看到" + (r.skipped().isEmpty() ? "" : "；" + String.join("、", r.skipped()) + " 已经有了，跳过"));
            }
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return requestsPage(back);
    }

    @PostMapping("/requests/{id}/reject")
    public String reject(@PathVariable Long id, @RequestParam(defaultValue = "") String reason,
                         @RequestParam(defaultValue = CodeRequest.PENDING) String back, RedirectAttributes ra) {
        try {
            CodeRequest r = agents.reject(id, reason, reviewer());
            ra.addFlashAttribute("msg", "已拒绝申请 #" + r.getId());
        } catch (ApiException e) {
            ra.addFlashAttribute("error", e.getMessage());
        }
        return requestsPage(back);
    }

    /** 批量同意（按申请的个数和次数生成）或拒绝。已经处理过的申请跳过并说明 */
    @PostMapping("/requests/bulk")
    public String bulkRequests(@RequestParam(required = false) List<Long> ids, @RequestParam(defaultValue = "") String action,
                               @RequestParam(defaultValue = "") String reason, @RequestParam(defaultValue = CodeRequest.PENDING) String back,
                               RedirectAttributes ra) {
        if (ids == null || ids.isEmpty()) {
            ra.addFlashAttribute("error", "先勾选要处理的申请");
            return requestsPage(back);
        }
        if (!action.equals("approve") && !action.equals("reject")) {
            ra.addFlashAttribute("error", "选同意或拒绝");
            return requestsPage(back);
        }
        int done = 0, codes = 0;
        List<String> skipped = new ArrayList<>();
        for (Long id : ids) {
            try {
                if (action.equals("approve")) {
                    codes += agents.approve(requireCodes(id).getId(), null, null, null, reviewer(), false, null).codes().size();
                } else {
                    agents.reject(id, reason, reviewer());
                }
                done++;
            } catch (ApiException e) {
                skipped.add(e.getMessage());
            }
        }
        String msg = action.equals("approve") ? "已同意 " + done + " 条申请，生成 " + codes + " 个口令" : "已拒绝 " + done + " 条申请";
        if (!skipped.isEmpty()) {
            msg += "；" + skipped.size() + " 条没处理：" + String.join("；", skipped.subList(0, Math.min(3, skipped.size())))
                    + (skipped.size() > 3 ? "…" : "");
        }
        ra.addFlashAttribute(done == 0 ? "error" : "msg", msg);
        return requestsPage(back);
    }

    // ===== 以代理身份预览 =====

    @GetMapping("/{id}/preview")
    public String preview(@PathVariable Long id, @RequestParam(defaultValue = "14") String range,
                          @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to, Model model) {
        return agentRepo.findById(id)
                .map(a -> portal.overview(a, "/admin/agents/" + id + "/preview", true, range, from, to, model))
                .orElse("redirect:/admin/agents");
    }

    @GetMapping("/{id}/preview/records")
    public String previewRecords(@PathVariable Long id,
                                 @RequestParam(defaultValue = "all") String status,
                                 @RequestParam(defaultValue = "") String code,
                                 @RequestParam(defaultValue = "1") int page,
                                 Model model) {
        Agent a = agentRepo.findById(id).orElse(null);
        if (a == null) return "redirect:/admin/agents";
        String base = "/admin/agents/" + id + "/preview";
        if (!Boolean.TRUE.equals(a.getCanViewRecords())) return "redirect:" + base;
        return portal.records(a, base, true, status, code, page, model);
    }

    @GetMapping("/{id}/preview/retention")
    public String previewRetention(@PathVariable Long id, @RequestParam(defaultValue = "30") String range,
                                   @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to, Model model) {
        Agent a = agentRepo.findById(id).orElse(null);
        if (a == null) return "redirect:/admin/agents";
        return portal.retention(a, "/admin/agents/" + id + "/preview", true, range, from, to, model);
    }

    @GetMapping("/{id}/preview/requests")
    public String previewRequests(@PathVariable Long id, Model model) {
        Agent a = agentRepo.findById(id).orElse(null);
        if (a == null) return "redirect:/admin/agents";
        String base = "/admin/agents/" + id + "/preview";
        if (!a.canRequest()) return "redirect:" + base;
        return portal.requests(a, base, true, model);
    }

    @GetMapping("/{id}/preview/account")
    public String previewAccount(@PathVariable Long id, Model model) {
        return agentRepo.findById(id)
                .map(a -> portal.account(a, "/admin/agents/" + id + "/preview", true, model))
                .orElse("redirect:/admin/agents");
    }

    @GetMapping("/{id}/preview/subs")
    public String previewSubs(@PathVariable Long id, @RequestParam(defaultValue = "30") String range,
                              @RequestParam(defaultValue = "") String from, @RequestParam(defaultValue = "") String to, Model model) {
        Agent a = agentRepo.findById(id).orElse(null);
        if (a == null) return "redirect:/admin/agents";
        String base = "/admin/agents/" + id + "/preview";
        if (!a.canManageSubs()) return "redirect:" + base;
        return portal.subs(a, base, true, range, from, to, model);
    }

    @GetMapping("/{id}/preview/notices")
    public String previewNotices(@PathVariable Long id, Model model) {
        return agentRepo.findById(id)
                .map(a -> portal.notices(a, "/admin/agents/" + id + "/preview", true, model))
                .orElse("redirect:/admin/agents");
    }

    // ===== 公告 =====

    @PostMapping("/notices")
    public String noticeCreate(@RequestParam String title, @RequestParam String body,
                               @RequestParam(defaultValue = "false") boolean pinned,
                               @RequestParam(defaultValue = "normal") String level,
                               @RequestParam(defaultValue = com.ota.platform.notice.Notice.ALL) String audience, RedirectAttributes ra) {
        String error = com.ota.platform.notice.NoticeService.validate(title, body);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/admin/agents#notices";
        }
        com.ota.platform.notice.Notice n = notices.create(title, body, pinned, level, audience, AuditService.operator());
        audit.log((Long) null, "NOTICE_CREATE", "发布公告「" + n.getTitle() + "」 · " + audienceText(n));
        ra.addFlashAttribute("msg", "已发布公告「" + n.getTitle() + "」，" + (n.isForSubs() ? "代理和二级代理" : "一级代理") + "登录后就能看到");
        return "redirect:/admin/agents#notices";
    }

    private static String audienceText(com.ota.platform.notice.Notice n) {
        return n.isForSubs() ? "发给全部代理（含二级代理）" : "只发一级代理";
    }

    @PostMapping("/notices/{nid}/update")
    public String noticeUpdate(@PathVariable Long nid, @RequestParam String title, @RequestParam String body,
                               @RequestParam(defaultValue = "false") boolean pinned,
                               @RequestParam(defaultValue = "normal") String level,
                               @RequestParam(defaultValue = com.ota.platform.notice.Notice.ALL) String audience, RedirectAttributes ra) {
        String error = com.ota.platform.notice.NoticeService.validate(title, body);
        if (error != null) {
            ra.addFlashAttribute("error", error);
            return "redirect:/admin/agents#notices";
        }
        notices.update(nid, title, body, pinned, level, audience).ifPresentOrElse(n -> {
            audit.log((Long) null, "NOTICE_UPDATE", "修改公告「" + n.getTitle() + "」 · " + audienceText(n));
            ra.addFlashAttribute("msg", "已保存公告「" + n.getTitle() + "」");
        }, () -> ra.addFlashAttribute("error", "公告不存在"));
        return "redirect:/admin/agents#notices";
    }

    @PostMapping("/notices/{nid}/pin")
    public String noticePin(@PathVariable Long nid, RedirectAttributes ra) {
        notices.pin(nid).ifPresent(n -> {
            audit.log("NOTICE_UPDATE", (n.isPinned() ? "置顶公告「" : "取消置顶公告「") + n.getTitle() + "」");
            ra.addFlashAttribute("msg", (n.isPinned() ? "已置顶「" : "已取消置顶「") + n.getTitle() + "」");
        });
        return "redirect:/admin/agents#notices";
    }

    @PostMapping("/notices/{nid}/delete")
    public String noticeDelete(@PathVariable Long nid, RedirectAttributes ra) {
        notices.delete(nid).ifPresent(n -> {
            audit.log("NOTICE_DELETE", "删除公告「" + n.getTitle() + "」");
            ra.addFlashAttribute("msg", "已删除公告「" + n.getTitle() + "」");
        });
        return "redirect:/admin/agents#notices";
    }

    // ===== 内部 =====

    private static String normalize(String username) {
        return username.trim().toLowerCase(Locale.ROOT);
    }

    private String validate(Agent self, String name, String login, String password, boolean requirePassword, String mode) {
        if (name.isBlank()) return "请填写代理名称";
        if (!USERNAME.matcher(login).matches()) return "登录账号需为 3–32 位小写字母、数字或 _ . -";
        if (settings.isAdminUsername(login) || adminUsers.find(login).isPresent()) return login + " 是管理员账号，不能用作代理账号";
        boolean taken = agentRepo.findByUsername(login).filter(o -> self == null || !o.getId().equals(self.getId())).isPresent();
        if (taken) return "登录账号 " + login + " 已被使用";
        if ((requirePassword || !password.isEmpty()) && password.length() < 6) return "密码至少 6 位";
        if (!MODES.contains(mode)) return "数据更新方式无效";
        return null;
    }

    private static String validateRequest(String requestMode, String dailyLimit) {
        if (!REQUEST_MODES.contains(requestMode)) return "申请口令的方式无效";
        int n = limitOf(dailyLimit);
        if (n < 0 || n > 100_000) return "每天能申请的个数填 0（不限）到 100000";
        return null;
    }

    /** 每天上限：留空是不限（0） */
    private static int limitOf(String dailyLimit) {
        return dailyLimit == null || dailyLimit.isBlank() ? 0 : toInt(dailyLimit);
    }

    private void apply(Agent a, String name, String login, boolean canViewRecords, String mode, String remark) {
        boolean becameManual = Agent.MODE_MANUAL.equals(mode) && !Agent.MODE_MANUAL.equals(a.getDataMode());
        a.setName(name.trim());
        a.setUsername(login);
        a.setCanViewRecords(canViewRecords);
        a.setDataMode(mode);
        a.setRemark(remark.trim());
        if (becameManual) a.setSnapshotAt(LocalDateTime.now());
    }

    private static void applyRequest(Agent a, String requestMode, String dailyLimit) {
        a.setRequestMode(requestMode);
        a.setDailyRequestLimit(Math.max(0, limitOf(dailyLimit)));
    }

    private static String validateSubs(String subLimit) {
        int n = limitOf(subLimit);
        if (n < 0 || n > 1000) return "最多开几个二级代理填 0（不限）到 1000";
        return null;
    }

    private static void applySubs(Agent a, boolean on, String subLimit) {
        a.setSubAgents(on);
        a.setSubAgentLimit(Math.max(0, limitOf(subLimit)));
    }

    private String describe(Agent a) {
        com.ota.platform.agent.Deduction d = com.ota.platform.agent.Deduction.parse(a.getDeductionTiers());
        return " · " + (Boolean.TRUE.equals(a.getCanViewRecords()) ? "可看记录" : "仅看数量")
                + " · " + ui.dataMode(a.getDataMode())
                + (d.hasTiers() ? " · 扣量：" + d.describe() : "")
                + " · 申请口令：" + requestDesc(a)
                + (a.canManageSubs() ? " · 可开二级代理" + (a.getSubAgentLimit() > 0 ? "（最多 " + a.getSubAgentLimit() + " 个）" : "") : "");
    }

    static String requestDesc(Agent a) {
        String limit = a.getDailyRequestLimit() <= 0 ? "每天不限" : "每天最多 " + a.getDailyRequestLimit() + " 个";
        return switch (a.getRequestMode()) {
            case Agent.REQUEST_REVIEW -> "需要审核，" + limit;
            case Agent.REQUEST_AUTO -> "自动通过，" + limit;
            default -> "不能申请";
        };
    }

    /** 同意申请要有「口令与激活」权限 */
    private CodeRequest requireCodes(Long id) {
        CodeRequest r = agents.find(id).orElseThrow(() -> ApiException.notFound("NO_REQUEST", "没有这条申请"));
        CurrentAdmin me = access.current();
        if (me == null || !me.canCodes()) {
            throw ApiException.forbidden("申请 #" + id + "：你没有「口令与激活」权限，不能生成口令");
        }
        return r;
    }

    /** 连口令一起删：要输自己的登录密码；名下有口令时还要有「口令与激活」权限 */
    private void checkPurge(List<Agent> list, String password) {
        CurrentAdmin me = access.current();
        if (me == null || !adminUsers.passwordMatches(me.id(), password)) {
            throw ApiException.badRequest("BAD_PASSWORD", "登录密码不对（连口令一起删要输入自己的登录密码）");
        }
        boolean hasCodes = list.stream().anyMatch(a -> !codeRepo.findByAgentIdOrderByCreatedAtDesc(a.getId()).isEmpty());
        if (hasCodes && !me.canCodes()) {
            throw ApiException.forbidden("名下有口令，你没有「口令与激活」权限，不能连口令一起删");
        }
    }

    private static String deletedText(AgentService.Deleted d, boolean purge) {
        String subs = d.subs() > 0 ? "，" + d.subs() + " 个二级代理一起删除" : "";
        if (d.codes() == 0) return "名下没有口令" + subs;
        return (purge ? "名下 " + d.codes() + " 个口令和 " + d.records() + " 条激活记录一起删除"
                : d.codes() + " 个口令改为未分配") + subs;
    }

    private String reviewer() {
        CurrentAdmin me = access.current();
        return me == null ? AuditService.operator() : me.name();
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();

    /** 审核弹窗里逐个勾选口令用 */
    private static String itemsJson(List<ItemView> its) {
        try {
            return JSON.writeValueAsString(its);
        } catch (Exception e) {
            return "[]";
        }
    }

    private static String statusLabel(CodeRequest r) {
        return switch (r.getStatus()) {
            case CodeRequest.PENDING -> "待审核";
            case CodeRequest.APPROVED -> r.isAuto() ? "自动通过" : "已同意";
            case CodeRequest.REJECTED -> "已拒绝";
            case CodeRequest.CANCELLED -> "代理撤回";
            default -> r.getStatus();
        };
    }

    private static String requestsPage(String back) {
        String f = REQUEST_FILTERS.contains(back) ? back : CodeRequest.PENDING;
        return "redirect:/admin/agents" + (CodeRequest.PENDING.equals(f) ? "" : "?req=" + f) + "#requests";
    }

    private LocalDateTime endOfDay(String date) {
        if (date == null || date.isBlank()) return null;
        try {
            return LocalDate.parse(date.trim()).atTime(23, 59, 59);
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_DATE", "有效期格式不对");
        }
    }

    private static String buildTiersJson(String[] mins, String[] maxes, String[] ratios) {
        return com.ota.platform.agent.Deduction.fromForm(mins, maxes, ratios);
    }

    private static int toInt(String s) {
        try {
            return s == null || s.isBlank() ? -1 : Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
