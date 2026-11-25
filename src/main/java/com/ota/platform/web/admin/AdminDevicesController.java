package com.ota.platform.web.admin;

import com.ota.platform.web.AdminUi;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.ota.DiagnoseService;
import com.ota.platform.ota.LaunchErrorService;
import com.ota.platform.ota.TestDevice;
import com.ota.platform.ota.TestDeviceService;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.stats.DeviceSeen;
import com.ota.platform.stats.DeviceSeenRepository;
import com.ota.platform.stats.StatsService;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

/** 设备：近 30 天的版本分布，按设备 ID 查某台设备正在运行哪个版本（排查「收不到更新」用）。 */
@Controller
@RequestMapping("/admin/devices")
public class AdminDevicesController {

    private static final int PAGE_SIZE = 50;

    private final DeviceSeenRepository devices;
    private final StatsService stats;
    private final ReleaseService releases;
    private final DeviceViews deviceViews;
    private final AdminShell shell;
    private final TestDeviceService testDevices;
    private final LaunchErrorService launchErrors;
    private final DiagnoseService diagnose;
    private final AppService apps;
    private final AdminUi ui;
    private final com.ota.platform.audit.AuditService audit;

    public AdminDevicesController(DeviceSeenRepository devices, StatsService stats, ReleaseService releases,
                                  DeviceViews deviceViews, AdminShell shell, TestDeviceService testDevices,
                                  LaunchErrorService launchErrors, DiagnoseService diagnose, AppService apps, AdminUi ui,
                                  com.ota.platform.audit.AuditService audit) {
        this.audit = audit;
        this.devices = devices;
        this.stats = stats;
        this.releases = releases;
        this.deviceViews = deviceViews;
        this.shell = shell;
        this.testDevices = testDevices;
        this.launchErrors = launchErrors;
        this.diagnose = diagnose;
        this.apps = apps;
        this.ui = ui;
    }

    /** 列表和导出共用；version=unknown 是认不出的版本 */
    private static Specification<DeviceSeen> filter(OtaApp app, DeviceViews.Distribution dist, String query, String plat, String ver) {
        return (root, cq, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("appId"), app.getId()));
            if (!query.isEmpty()) where.add(cb.like(root.get("clientId"), "%" + query.replace("%", "") + "%"));
            if (!"all".equals(plat)) where.add(cb.equal(root.get("platform"), plat));
            if ("unknown".equals(ver)) {
                List<String> known = new ArrayList<>(dist.labels().keySet());
                known.add(StatsService.EMBEDDED);
                where.add(cb.or(cb.isNull(root.get("currentUpdateId")), cb.not(root.get("currentUpdateId").in(known))));
            } else if (!ver.isEmpty()) {
                where.add(cb.equal(root.get("currentUpdateId"), ver));
            }
            return cb.and(where.toArray(new Predicate[0]));
        };
    }

    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> export(@RequestParam(defaultValue = "") String q,
                                                                 @RequestParam(defaultValue = "all") String platform,
                                                                 @RequestParam(defaultValue = "") String version,
                                                                 HttpSession session) {
        OtaApp app = shell.current(session);
        if (app == null) return org.springframework.http.ResponseEntity.status(404).build();
        DeviceViews.Distribution dist = deviceViews.distribution(app);
        String plat = "ios".equals(platform) || "android".equals(platform) ? platform : "all";
        List<DeviceSeen> rows = devices.findAll(filter(app, dist, q.trim(), plat, version.trim()),
                PageRequest.of(0, 100_000, Sort.by(Sort.Direction.DESC, "lastSeen"))).getContent();
        Map<String, String> labels = new HashMap<>(dist.labels());
        com.ota.platform.web.Csv csv = new com.ota.platform.web.Csv().row("设备 ID", "平台", "当前版本", "渠道", "Runtime", "第一次出现", "最近检查更新");
        for (DeviceSeen d : rows) {
            String id = d.getCurrentUpdateId();
            String label = id == null ? "未知" : StatsService.EMBEDDED.equals(id) ? "内置版本"
                    : labels.computeIfAbsent(id, k -> releases.find(k).filter(x -> x.getAppId().equals(app.getId())).map(x -> x.shortId()).orElse(k.length() > 8 ? k.substring(0, 8) : k));
            csv.row(d.getClientId(), "ios".equals(d.getPlatform()) ? "iOS" : "android".equals(d.getPlatform()) ? "Android" : d.getPlatform(), label,
                    d.getChannel() == null ? "" : d.getChannel(), d.getRuntimeVersion() == null ? "" : d.getRuntimeVersion(),
                    ui.full(d.getFirstSeen()), ui.full(d.getLastSeen()));
        }
        audit.log("DEVICE_EXPORT", app.getSlug() + " 导出 " + rows.size() + " 台设备");
        return csv.download(app.getSlug() + "-设备-" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".csv");
    }

    /** 设备构成里的一行：名称、台数、占比 */
    public record Share(String label, long count, long pct) {}

    /** 按台数从多到少，最多 5 行，其余合成「其他」 */
    static List<Share> shares(List<Object[]> rows, String empty) {
        Map<String, Long> by = new HashMap<>();
        long total = 0;
        for (Object[] r : rows) {
            String k = r[0] == null || r[0].toString().isBlank() ? empty : r[0].toString();
            long n = ((Number) r[1]).longValue();
            by.merge(k, n, Long::sum);
            total += n;
        }
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(by.entrySet());
        sorted.sort(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()));
        List<Share> out = new ArrayList<>();
        long rest = 0;
        for (int i = 0; i < sorted.size(); i++) {
            if (i < 5) out.add(new Share(sorted.get(i).getKey(), sorted.get(i).getValue(), 0));
            else rest += sorted.get(i).getValue();
        }
        if (rest > 0) out.add(new Share("其他", rest, 0));
        long sum = total;
        return out.stream().map(s -> new Share(s.label(), s.count(), com.ota.platform.common.Percent.of(s.count(), sum))).toList();
    }

    /** 模拟检查更新，返回结论和每一步的说明 */
    @GetMapping("/diagnose")
    @ResponseBody
    public Map<String, Object> diagnose(@RequestParam(defaultValue = "") String device, @RequestParam(defaultValue = "ios") String platform,
                                        @RequestParam(defaultValue = "") String runtime, @RequestParam(defaultValue = "") String channel,
                                        HttpSession session) {
        OtaApp app = shell.current(session);
        Map<String, Object> m = new LinkedHashMap<>();
        if (app == null) {
            m.put("ok", false);
            m.put("msg", "没有 App");
            return m;
        }
        DiagnoseService.Result r = diagnose.diagnose(app, device, platform, runtime, channel);
        m.put("ok", true);
        m.put("verdict", r.verdict());
        m.put("level", r.level());
        m.put("releaseId", r.releaseId());
        m.put("steps", r.steps());
        return m;
    }

    @GetMapping
    public String page(@RequestParam(defaultValue = "") String q,
                       @RequestParam(defaultValue = "all") String platform,
                       @RequestParam(defaultValue = "") String version,
                       @RequestParam(defaultValue = "1") int page,
                       Model model, HttpSession session) {
        OtaApp app = shell.populate(model, session);
        if (app == null) return "redirect:/admin/apps";
        DeviceViews.Distribution dist = deviceViews.distribution(app);
        AdminController.devices(model, app.getId(), stats, 30);

        String query = q.trim();
        String plat = "ios".equals(platform) || "android".equals(platform) ? platform : "all";
        String ver = version.trim();
        Specification<DeviceSeen> spec = filter(app, dist, query, plat, ver);
        Page<DeviceSeen> list = devices.findAll(spec, PageRequest.of(Math.max(page, 1) - 1, PAGE_SIZE, Sort.by(Sort.Direction.DESC, "lastSeen")));
        Map<String, String> labels = new HashMap<>(dist.labels());
        for (DeviceSeen d : list) {
            String id = d.getCurrentUpdateId();
            if (id == null || labels.containsKey(id) || StatsService.EMBEDDED.equals(id)) continue;
            releases.find(id).filter(x -> x.getAppId().equals(app.getId())).ifPresent(x -> labels.put(id, x.shortId()));
        }

        Map<String, Long> byPlatform = stats.devicesByPlatform(app.getId());
        Map<String, TestDevice> testers = new HashMap<>();
        for (TestDevice t : testDevices.list(app)) testers.put(t.getClientId().trim().toLowerCase(Locale.ROOT), t);
        model.addAttribute("testers", testers);
        model.addAttribute("launchErrors", launchErrors.recent(app.getId()));
        model.addAttribute("channels", apps.channels(app));
        Set<String> runtimes = new TreeSet<>(Comparator.reverseOrder());
        for (com.ota.platform.app.Channel c : apps.channels(app)) for (com.ota.platform.release.Deployment d : releases.deployments(c)) runtimes.add(d.getRuntimeVersion());
        model.addAttribute("runtimes", runtimes);
        model.addAttribute("dist", dist);
        java.time.Instant since = java.time.Instant.now().minus(java.time.Duration.ofDays(30));
        model.addAttribute("byChannel", shares(devices.countByChannel(app.getId(), since), "production"));
        model.addAttribute("byRuntime", shares(devices.countByRuntime(app.getId(), since), "未上报"));
        model.addAttribute("ios", byPlatform.getOrDefault("ios", 0L));
        model.addAttribute("android", byPlatform.getOrDefault("android", 0L));
        model.addAttribute("devices", list);
        model.addAttribute("labels", labels);
        model.addAttribute("q", query);
        model.addAttribute("platform", plat);
        model.addAttribute("version", ver);
        model.addAttribute("filtered", !query.isEmpty() || !"all".equals(plat) || !ver.isEmpty());
        return "admin/devices";
    }
}
