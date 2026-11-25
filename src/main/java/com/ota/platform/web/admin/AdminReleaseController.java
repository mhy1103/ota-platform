package com.ota.platform.web.admin;

import com.ota.platform.web.AdminUi;
import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AppPerm;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.app.AppService;
import com.ota.platform.app.Channel;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.common.ApiException;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.installer.InstallerPackage;
import com.ota.platform.installer.InstallerService;
import com.ota.platform.ota.*;
import com.ota.platform.patch.Patch;
import com.ota.platform.patch.PatchService;
import com.ota.platform.pkg.ExternalPackage;
import com.ota.platform.pkg.PackageFiles;
import com.ota.platform.pkg.PackageImporter;
import com.ota.platform.pkg.VerifyService;
import com.ota.platform.release.Asset;
import com.ota.platform.release.AssetRepository;
import com.ota.platform.release.Deployment;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseFile;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.stats.StatsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Map;

/** 版本发布：上传 ZIP、添加外部链接、发布到渠道、灰度、回滚、回退内置版本、校验、删除。 */
@Controller
@RequestMapping("/admin")
public class AdminReleaseController {

    private final AppService apps;
    private final ReleaseService releases;
    private final PackageImporter importer;
    private final ExternalPackage externalPackage;
    private final VerifyService verifier;
    private final ReleaseViews views;
    private final StatsService stats;
    private final AdminShell shell;
    private final AuditService audit;
    private final OtaProperties props;
    private final AssetRepository assetRepo;
    private final PatchService patchService;
    private final AdminUi ui;
    private final OtaEvents events;
    private final ReleaseStatsService releaseStats;
    private final LaunchErrorService launchErrors;
    private final AccessService access;

    /** 每个 Runtime 默认列出的版本数，其余点「显示全部」 */
    static final int LIST_LIMIT = 20;

    private final ReleaseStructure structure;
    private final InstallerService installers;
    private final com.ota.platform.installer.ChunkedUploads chunks;
    private final com.ota.platform.schedule.ScheduleService schedules;
    private final ReleaseGuard guard;

    public AdminReleaseController(AppService apps, ReleaseService releases, PackageImporter importer, ExternalPackage externalPackage,
                                  VerifyService verifier, ReleaseViews views, StatsService stats, AdminShell shell,
                                  AuditService audit, OtaProperties props, AssetRepository assetRepo, PatchService patchService,
                                  AdminUi ui, OtaEvents events, ReleaseStatsService releaseStats, LaunchErrorService launchErrors,
                                  AccessService access, ReleaseStructure structure, InstallerService installers,
                                  com.ota.platform.installer.ChunkedUploads chunks, com.ota.platform.schedule.ScheduleService schedules,
                                  ReleaseGuard guard) {
        this.guard = guard;
        this.schedules = schedules;
        this.structure = structure;
        this.installers = installers;
        this.chunks = chunks;
        this.apps = apps;
        this.releases = releases;
        this.importer = importer;
        this.externalPackage = externalPackage;
        this.verifier = verifier;
        this.views = views;
        this.stats = stats;
        this.shell = shell;
        this.audit = audit;
        this.props = props;
        this.assetRepo = assetRepo;
        this.patchService = patchService;
        this.ui = ui;
        this.events = events;
        this.releaseStats = releaseStats;
        this.launchErrors = launchErrors;
        this.access = access;
    }

    @GetMapping("/releases")
    public String page(@RequestParam(defaultValue = "false") boolean all, Model model, HttpSession session) {
        OtaApp app = shell.populate(model, session);
        if (app == null) return "redirect:/admin/apps";
        List<ReleaseViews.Live> live = views.live(app);
        model.addAttribute("live", live);
        List<ReleaseViews.Group> groups = views.groups(app, live, all ? Integer.MAX_VALUE : LIST_LIMIT);
        model.addAttribute("groups", groups);
        model.addAttribute("choices", views.choices(app));
        model.addAttribute("history", releases.history(app, 10));
        model.addAttribute("plans", schedules.pending(app.getId()));
        model.addAttribute("planLog", schedules.recent(app.getId()));
        model.addAttribute("all", all);
        List<String> ids = new ArrayList<>();
        for (ReleaseViews.Group g : groups) for (ReleaseViews.Row r : g.getRows()) ids.add(r.getRelease().getId());
        for (ReleaseViews.Live l : live) if (l.getRelease() != null) ids.add(l.getRelease().getId());
        Map<String, ReleaseStat> st = releaseStats.get(ids);
        model.addAttribute("stats", st);
        model.addAttribute("alerts", ReleaseViews.alarms(live, st));
        model.addAttribute("channels", apps.channels(app));
        model.addAttribute("activeDevices", stats.activeDevices(app.getId()));
        model.addAttribute("manifestUrl", props.baseUrl() + "/m/" + app.getSlug());
        model.addAttribute("patchOn", props.getPatch().isEnabled() && app.isPatchEnabled());
        model.addAttribute("installers", installerViews(app));
        model.addAttribute("guard", guard.config(app.getId()));
        model.addAttribute("guardRates", ReleaseGuard.RATES);
        model.addAttribute("guardMins", ReleaseGuard.MINIMUMS);
        return "admin/releases";
    }

    /** 启动失败自动回滚：开关、失败率、至少几台 */
    @PostMapping("/guard")
    public String saveGuard(@RequestParam(defaultValue = "false") boolean on, @RequestParam(defaultValue = "10") int rate,
                            @RequestParam(defaultValue = "5") int min, HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        ReleaseGuard.Config c = guard.save(app.getId(), on, rate, min);
        String rule = "启动失败率 ≥" + c.rate() + "% 且 ≥" + c.min() + " 台";
        audit.log("RELEASE_GUARD", app.getSlug() + (c.on() ? " 开启了自动回滚：" + rule + " 时回滚到上一版本" : " 关闭了自动回滚"));
        ra.addFlashAttribute("msg", c.on() ? "已开启自动回滚：线上版本" + rule + "时，自动回滚到上一版本" : "已关闭自动回滚，启动失败偏多时照常只告警");
        return "redirect:/admin/releases";
    }

    /** 安装包列表：读出来的配置和检查结果 */
    public record InstallerView(String id, String platform, String fileName, String size, String bundleId, String version, String runtime,
                                String channel, String updateUrl, String sdk, String embedded, String note, String uploadedAt,
                                String uploadedBy, String level, String headline, List<InstallerService.Check> checks, List<String> warnings) {}

    private List<InstallerView> installerViews(OtaApp app) {
        List<InstallerView> out = new ArrayList<>();
        for (InstallerPackage p : installers.list(app)) {
            List<InstallerService.Check> checks = installers.checks(app, p);
            String level = InstallerService.level(checks);
            long bad = checks.stream().filter(c -> "error".equals(c.state())).count();
            long warn = checks.stream().filter(c -> "warn".equals(c.state())).count();
            String headline = bad > 0 ? bad + " 项不通过" : warn > 0 ? "能收到更新，" + warn + " 项要注意" : "能收到这个 App 的更新";
            String version = (p.getVersionName() == null ? "" : p.getVersionName()) + (p.getBuildNumber() == null ? "" : "（" + p.getBuildNumber() + "）");
            out.add(new InstallerView(p.getId(), "ios".equals(p.getPlatform()) ? "iOS" : "Android", p.getFileName(), ui.size(p.getSize()),
                    p.getBundleId(), version.isEmpty() ? null : version, p.getRuntimeVersion(), p.getChannel(), p.getUpdateUrl(),
                    p.getSdkVersion(), p.getEmbeddedUpdateId() == null ? null : ui.rid(p.getEmbeddedUpdateId()), p.getNote(),
                    ui.dt(p.getUploadedAt()), p.getUploadedBy(), level, headline, checks, warnings(p)));
        }
        return out;
    }

    /** 早期版本存下的提示：运行版本其实读到了，不用再提醒 */
    private static List<String> warnings(InstallerPackage p) {
        if (p.getWarnings() == null) return List.of();
        return java.util.Arrays.stream(p.getWarnings().split("\n"))
                .filter(w -> !(p.getRuntimeVersion() != null && w.startsWith("app.config 里的 runtimeVersion 是策略"))).toList();
    }

    // ==================== 安装包（可选） ====================

    @PostMapping("/installers")
    @ResponseBody
    public Map<String, Object> uploadInstaller(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "") String note,
                                               HttpSession session) {
        OtaApp app = shell.current(session);
        if (app == null) return result(false, "没有 App");
        try {
            return uploaded(app, installers.upload(app, file, note, who()));
        } catch (ApiException e) {
            return result(false, e.getMessage());
        } catch (Exception e) {
            return result(false, "上传失败：" + reason(e));
        }
    }

    /** 分片上传（大文件绕开反向代理的大小限制）：先登记文件名和大小，再按顺序传每一片，最后合成 */
    @PostMapping("/installers/upload/start")
    @ResponseBody
    public Map<String, Object> startInstallerUpload(@RequestParam String fileName, @RequestParam long size, HttpSession session) {
        OtaApp app = shell.current(session);
        if (app == null) return result(false, "没有 App");
        try {
            Map<String, Object> r = result(true, "");
            r.put("id", chunks.start(who(), app.getId(), fileName, size));
            return r;
        } catch (ApiException e) {
            return result(false, e.getMessage());
        } catch (Exception e) {
            return result(false, "上传失败：" + reason(e));
        }
    }

    @PostMapping(value = "/installers/upload/{id}", consumes = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseBody
    public Map<String, Object> appendInstallerUpload(@PathVariable String id, @RequestParam long offset,
                                                     jakarta.servlet.http.HttpServletRequest req, HttpSession session) {
        OtaApp app = shell.current(session);
        if (app == null) return result(false, "没有 App");
        try {
            Map<String, Object> r = result(true, "");
            r.put("received", chunks.append(id, who(), app.getId(), offset, req.getInputStream()));
            return r;
        } catch (ApiException e) {
            return result(false, e.getMessage());
        } catch (Exception e) {
            return result(false, "上传失败：" + reason(e));
        }
    }

    @PostMapping("/installers/upload/{id}/finish")
    @ResponseBody
    public Map<String, Object> finishInstallerUpload(@PathVariable String id, @RequestParam(defaultValue = "") String note, HttpSession session) {
        OtaApp app = shell.current(session);
        if (app == null) return result(false, "没有 App");
        java.nio.file.Path file = null;
        try {
            com.ota.platform.installer.ChunkedUploads.Done done = chunks.finish(id, who(), app.getId());
            file = done.file();
            return uploaded(app, installers.store(app, file, done.fileName(), note, who()));
        } catch (ApiException e) {
            return result(false, e.getMessage());
        } catch (Exception e) {
            return result(false, "上传失败：" + reason(e));
        } finally {
            if (file != null) {
                try {
                    java.nio.file.Files.deleteIfExists(file);
                } catch (java.io.IOException ignored) {
                    // 临时文件下次清理
                }
            }
        }
    }

    private Map<String, Object> uploaded(OtaApp app, InstallerPackage p) {
        String level = InstallerService.level(installers.checks(app, p));
        audit.log("INSTALLER_UPLOAD", app.getSlug() + " 上传安装包 " + p.getFileName() + "（" + InstallerService.title(p)
                + "，运行版本 " + (p.getRuntimeVersion() == null ? "未知" : p.getRuntimeVersion()) + "）");
        return result(true, "已上传 " + InstallerService.title(p) + (level.equals("error") ? "，有检查项不通过，看一下列表里的说明" : ""));
    }

    @PostMapping("/installers/{id}/delete")
    public String deleteInstaller(@PathVariable String id, HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        installers.find(app, id).ifPresentOrElse(p -> {
            installers.delete(p);
            audit.log("INSTALLER_DELETE", app.getSlug() + " 删除安装包 " + p.getFileName());
            ra.addFlashAttribute("msg", "已删除安装包 " + p.getFileName());
        }, () -> ra.addFlashAttribute("error", "安装包不存在"));
        return "redirect:/admin/releases#installers";
    }

    @GetMapping("/installers/{id}/download")
    public ResponseEntity<org.springframework.core.io.Resource> downloadInstaller(@PathVariable String id, HttpSession session) throws java.io.IOException {
        OtaApp app = shell.current(session);
        InstallerPackage p = app == null ? null : installers.find(app, id).orElse(null);
        if (p == null) return ResponseEntity.notFound().build();
        String name = java.net.URLEncoder.encode(p.getFileName(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename*=UTF-8''" + name)
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(p.getSize())
                .body(new org.springframework.core.io.InputStreamResource(installers.open(p)));
    }

    // ==================== 新版本 ====================

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "") String note,
                         @RequestParam(defaultValue = "") String runtime, @RequestParam(defaultValue = "false") boolean forceUpdate,
                         @RequestParam(defaultValue = "false") boolean activate, @RequestParam(defaultValue = "production") String channel,
                         @RequestParam(defaultValue = "hot") String as, HttpSession session, RedirectAttributes ra) {
        Map<String, Object> r = uploadAjax(file, note, runtime, forceUpdate, activate, channel, as, session);
        ra.addFlashAttribute(Boolean.TRUE.equals(r.get("ok")) ? "msg" : "error", r.get("msg"));
        return "redirect:/admin/releases";
    }

    /** 上传弹窗走 XHR 以显示进度，返回 JSON。 */
    @PostMapping(value = "/upload", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public Map<String, Object> uploadAjax(@RequestParam("file") MultipartFile file, @RequestParam(defaultValue = "") String note,
                                          @RequestParam(defaultValue = "") String runtime,
                                          @RequestParam(defaultValue = "false") boolean forceUpdate,
                                          @RequestParam(defaultValue = "false") boolean activate,
                                          @RequestParam(defaultValue = "production") String channel,
                                          @RequestParam(defaultValue = "hot") String as,
                                          HttpSession session) {
        OtaApp app = shell.current(session);
        if (app == null) return result(false, "还没有 App，先在「所有 App」里新建");
        try {
            Release r = importer.importZip(app, file, note, forceUpdate, runtime.isBlank() ? null : runtime.trim(), who());
            audit.log("UPLOAD", app.getSlug() + " 上传版本 " + r.shortId() + "（Runtime " + r.getRuntimeVersion() + "，" + r.getPlatforms()
                    + "，" + PackageFiles.size(r.getTotalBytes()) + (forceUpdate ? "，强制更新" : "") + "）");
            String msg = "已上传 " + r.shortId() + "（Runtime " + r.getRuntimeVersion() + "）";
            if (activate) msg += publish(app, channel, r, as);
            return result(true, msg);
        } catch (Exception e) {
            return result(false, "上传失败：" + reason(e));
        }
    }

    /** 外部链接版本：dist 目录放在 CDN 上，服务器读一遍算出哈希，App 直接从 CDN 下载 */
    @PostMapping("/addUrl")
    public String addUrl(@RequestParam String externalUrl, @RequestParam(defaultValue = "") String note,
                         @RequestParam(defaultValue = "false") boolean forceUpdate,
                         @RequestParam(defaultValue = "false") boolean activate, @RequestParam(defaultValue = "production") String channel,
                         @RequestParam(defaultValue = "hot") String as, HttpSession session, RedirectAttributes ra) {
        Map<String, Object> r = addUrlAjax(externalUrl, note, forceUpdate, activate, channel, as, "", session);
        ra.addFlashAttribute(Boolean.TRUE.equals(r.get("ok")) ? "msg" : "error", r.get("msg"));
        return "redirect:/admin/releases";
    }

    /** read：页面先读好的那次（见 /addUrl/read），读完了就不用再读一遍 */
    @PostMapping(value = "/addUrl", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public Map<String, Object> addUrlAjax(@RequestParam String externalUrl, @RequestParam(defaultValue = "") String note,
                                          @RequestParam(defaultValue = "false") boolean forceUpdate,
                                          @RequestParam(defaultValue = "false") boolean activate,
                                          @RequestParam(defaultValue = "production") String channel,
                                          @RequestParam(defaultValue = "hot") String as,
                                          @RequestParam(defaultValue = "") String read,
                                          HttpSession session) {
        OtaApp app = shell.current(session);
        if (app == null) return result(false, "还没有 App，先在「所有 App」里新建");
        try {
            ExternalPackage.Fetched f;
            if (read.isEmpty()) {
                f = externalPackage.fetch(externalUrl);
            } else {
                f = alreadyRead(read, externalUrl);
                if (f == null) {
                    Map<String, Object> m = result(false, "读好的结果用不了了（链接里的文件换过，或者服务重启过），正在重新读取");
                    m.put("reread", true);
                    return m;
                }
            }
            Release r = releases.createExternal(app, f, note, forceUpdate, who());
            audit.log("ADD_URL", app.getSlug() + " 添加外部链接版本 " + r.shortId() + " → " + f.baseUrl() + "（" + f.files().size()
                    + " 个文件，" + PackageFiles.size(f.totalBytes()) + "）");
            String msg = "已添加外部链接版本 " + r.shortId() + "（" + f.files().size() + " 个文件，" + PackageFiles.size(f.totalBytes()) + "）";
            if (activate) msg += publish(app, channel, r, as) + "。App 会直接从外部链接下载";
            return result(true, msg);
        } catch (Exception e) {
            return result(false, "添加失败：" + reason(e));
        }
    }

    // ===== 外部链接先在后台读：填好地址就开始读，页面显示进度；点「添加」时读完了直接用 =====
    // 链接慢的时候（文件多、服务器带宽小）要读好几分钟，放在添加的那一个请求里读，页面一直转圈，还会被反向代理超时打断

    private static final class LinkRead {
        final long adminId;
        final String base;
        final long started = System.currentTimeMillis();
        final ExternalPackage.Progress progress = new ExternalPackage.Progress();
        volatile ExternalPackage.Fetched fetched;
        volatile String error;
        volatile long finished;

        LinkRead(long adminId, String base) {
            this.adminId = adminId;
            this.base = base;
        }
    }

    private final Map<String, LinkRead> reads = new ConcurrentHashMap<>();
    private final ExecutorService linkReader = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ota-link-read");
        t.setDaemon(true);
        return t;
    });

    /** 开始读一个外部链接。同一个人读同一个地址（正在读、读完没出错）就接着用那次；换了地址，之前没读完的停掉 */
    @PostMapping(value = "/addUrl/read", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public Map<String, Object> readLink(@RequestParam String externalUrl) {
        CurrentAdmin me = access.current();
        if (me == null) return result(false, "登录已过期，请刷新页面后重新登录");
        String base;
        try {
            base = externalPackage.baseOf(externalUrl).toString();
        } catch (PackageFiles.PackageException e) {
            return result(false, e.getMessage());
        }
        long now = System.currentTimeMillis();
        reads.values().removeIf(r -> r.finished > 0 && now - r.finished > 30 * 60_000L);
        String id = null;
        for (Map.Entry<String, LinkRead> e : reads.entrySet()) {
            LinkRead r = e.getValue();
            if (r.adminId != me.id()) continue;
            if (r.base.equals(base) && r.error == null && !r.progress.cancelled) id = e.getKey();
            else if (r.finished == 0) r.progress.cancelled = true;
        }
        if (id == null) {
            LinkRead r = new LinkRead(me.id(), base);
            id = UUID.randomUUID().toString();
            reads.put(id, r);
            linkReader.execute(() -> {
                try {
                    r.fetched = externalPackage.fetch(base, r.progress);
                } catch (Exception e) {
                    r.error = reason(e);
                } finally {
                    r.finished = System.currentTimeMillis();
                }
            });
        }
        Map<String, Object> m = result(true, "");
        m.put("id", id);
        return m;
    }

    /** state：reading 还在读；done 读完了；failed 出错（msg 是原因）。reading 是正在下载的文件，剩得最多的在前 */
    @GetMapping("/addUrl/read/{id}")
    @ResponseBody
    public Map<String, Object> readLinkStatus(@PathVariable String id) {
        LinkRead r = reads.get(id);
        CurrentAdmin me = access.current();
        if (r == null || me == null || r.adminId != me.id()) return result(false, "这次读取已经过期，请重新读取");
        ExternalPackage.Progress p = r.progress;
        Map<String, Object> m = result(true, r.error == null ? "" : r.error);
        m.put("state", r.error != null ? "failed" : r.fetched != null ? "done" : "reading");
        m.put("total", p.total);
        m.put("done", p.done.get());
        m.put("bytes", p.bytes.get());
        m.put("elapsed", (r.finished > 0 ? r.finished : System.currentTimeMillis()) - r.started);
        List<Map<String, Object>> now = new ArrayList<>();
        p.reading.forEach((path, slot) -> {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("path", path);
            f.put("read", slot[0]);
            f.put("size", slot[1]);
            now.add(f);
        });
        now.sort((a, b) -> Long.compare(left(b), left(a)));
        m.put("reading", now.subList(0, Math.min(3, now.size())));
        if (r.fetched != null) {
            m.put("files", r.fetched.files().size());
            m.put("size", PackageFiles.size(r.fetched.totalBytes()));
        }
        return m;
    }

    /** 还剩多少没下载；还没收到响应、不知道大小的排在最后 */
    private static long left(Map<String, Object> f) {
        long size = (long) f.get("size"), read = (long) f.get("read");
        return size < 0 ? -1 : size - read;
    }

    @PostMapping(value = "/addUrl/read/{id}/cancel", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public Map<String, Object> cancelReadLink(@PathVariable String id) {
        LinkRead r = reads.get(id);
        CurrentAdmin me = access.current();
        if (r != null && me != null && r.adminId == me.id()) {
            r.progress.cancelled = true;
            reads.remove(id, r);
        }
        return result(true, "");
    }

    /** 页面先读好的：同一个人、同一个地址、读完没出错，并且链接里的 dist 没换掉，才直接用。用过就丢掉 */
    private ExternalPackage.Fetched alreadyRead(String id, String url) throws PackageFiles.PackageException {
        LinkRead r = id.isEmpty() ? null : reads.get(id);
        CurrentAdmin me = access.current();
        if (r == null || me == null || r.adminId != me.id() || r.fetched == null) return null;
        reads.remove(id, r);
        if (!r.base.equals(externalPackage.baseOf(url).toString())) return null;
        return externalPackage.unchanged(r.fetched) ? r.fetched : null;
    }

    // ==================== 发布、灰度、回滚 ====================

    /** as=activation 时设成口令更新包（不灰度），不传或 hot 是平常的发布 */
    @PostMapping("/deploy")
    public String deploy(@RequestParam String releaseId, @RequestParam(defaultValue = "production") String channel,
                         @RequestParam(defaultValue = "100") int rollout, @RequestParam(defaultValue = "hot") String as,
                         HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        try {
            Release r = releases.requireRelease(app, releaseId);
            if (!hot(as)) {
                boolean changed = !activate(app, channel, r).isEmpty();
                ra.addFlashAttribute("msg", changed
                        ? "已把 " + r.shortId() + " 设为 " + channel + " 的口令更新包：新装 App 输入口令激活时下载它，已激活的设备照常拿常规更新包"
                        : r.shortId() + " 本来就是 " + channel + " 的口令更新包");
            } else {
                StringBuilder msg = new StringBuilder();
                Release live = deployHot(app, channel, r, rollout, msg);
                String text = msg.toString().replaceFirst("^，并", "");
                if (activation(as)) {
                    String done = activate(app, channel, live);
                    text += done.isEmpty() ? "，它本来就是 " + channel + " 的口令更新包" : done.replaceFirst("^，并已", "，同时");
                }
                ra.addFlashAttribute("msg", text + "，设备下次检查更新时生效");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("error", "发布失败：" + reason(e));
        }
        return "redirect:/admin/releases";
    }

    /** 取消口令更新包：还在内置版本的设备改回拿常规更新包 */
    @PostMapping("/activation/clear")
    public String clearActivation(@RequestParam String channel, @RequestParam String runtime, HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        try {
            Channel c = apps.requireChannel(app, channel);
            Deployment d = releases.clearActivation(app, c, runtime, who());
            events.activationCleared(app, c.getName(), d.getRuntimeVersion());
            audit.log("ACTIVATION_PACKAGE", app.getSlug() + " · " + c.getName() + " 取消口令更新包（Runtime " + d.getRuntimeVersion() + "）");
            ra.addFlashAttribute("msg", "已取消 " + c.getName() + " 的口令更新包，输入口令激活时改回拿常规更新包");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "取消失败：" + reason(e));
        }
        return "redirect:/admin/releases";
    }

    /** 调整线上版本的灰度比例 */
    @PostMapping("/rollout")
    public String rollout(@RequestParam String channel, @RequestParam String runtime, @RequestParam int rollout,
                          HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        try {
            Channel c = apps.requireChannel(app, channel);
            String current = releases.requireDeployment(c, runtime).getReleaseId();
            if (current == null) throw ApiException.badRequest("NO_RELEASE", "这个渠道还没有线上版本");
            ReleaseService.Deployed d = releases.deploy(app, c, current, rollout, who());
            audit.log("ACTIVATE", app.getSlug() + " · " + channel + " Runtime " + runtime + " 灰度调整为 " + rollout + "%");
            events.deployed(app, d.release(), c.getName(), rollout, d.republished());
            ra.addFlashAttribute("msg", channel + " 的灰度已调整为 " + rollout + "%");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "调整失败：" + reason(e));
        }
        return "redirect:/admin/releases";
    }

    /** 回滚：to 为空回到上一版本，也可以选更早的任意版本 */
    @PostMapping("/rollback")
    public String rollback(@RequestParam String channel, @RequestParam String runtime, @RequestParam(defaultValue = "") String to,
                           HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        try {
            Channel c = apps.requireChannel(app, channel);
            ReleaseService.Deployed d = releases.rollback(app, c, runtime, to, who());
            events.rolledBack(app, d.release(), c.getName());
            String from = d.release().getRepublishedFrom() != null ? d.release().getRepublishedFrom().substring(0, 8) : d.release().shortId();
            audit.log("ROLLBACK", app.getSlug() + " · " + channel + " 回滚到 " + from + "（新版本 " + d.release().shortId() + "，Runtime " + runtime + "）");
            ra.addFlashAttribute("msg", d.republished()
                    ? "已回滚到 " + from + "。为了让已经更新的设备也换回去，复制成了新版本 " + d.release().shortId() + " 发布"
                    : "已回滚到 " + from + "，设备下次检查更新时生效");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "回滚失败：" + reason(e));
        }
        return "redirect:/admin/releases";
    }

    @PostMapping("/rollback-embedded")
    public String rollbackEmbedded(@RequestParam String channel, @RequestParam String runtime,
                                   HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        try {
            releases.rollbackToEmbedded(app, apps.requireChannel(app, channel), runtime, who());
            events.rolledBackToEmbedded(app, channel, runtime);
            audit.log("ROLLBACK", app.getSlug() + " · " + channel + " 回退到内置版本（Runtime " + runtime + "）");
            ra.addFlashAttribute("msg", channel + " 的设备下次检查更新时会回到安装包内置的版本；再发布任意版本即可恢复");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "操作失败：" + reason(e));
        }
        return "redirect:/admin/releases";
    }

    /** 校验某个版本：文件、哈希、Runtime、签名；外部链接先下载再校验 */
    @PostMapping("/verify")
    @ResponseBody
    public Map<String, Object> verify(@RequestParam String releaseId, HttpSession session) {
        OtaApp app = shell.current(session);
        if (app == null) return result(false, "没有 App");
        Release r = releases.requireRelease(app, releaseId);
        VerifyService.Result result = verifier.verify(app, r);
        audit.log("VERIFY", app.getSlug() + " 校验 " + r.shortId() + "：" + result.getHeadline());
        return result.toMap();
    }

    /** 包结构：换一个对比版本（base 为空表示不对比） */
    @GetMapping("/release/{id}/structure")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> structure(@PathVariable String id, @RequestParam(defaultValue = "") String base, HttpSession session) {
        OtaApp app = shell.current(session);
        Release r = app == null ? null : releases.find(id).filter(x -> x.getAppId().equals(app.getId())).orElse(null);
        if (r == null) return ResponseEntity.status(404).body(result(false, "版本不存在"));
        Release b = base.isBlank() ? null : releases.find(base).filter(x -> x.getAppId().equals(app.getId())).orElse(null);
        Map<String, Object> m = new LinkedHashMap<>(structure.build(r, b));
        m.put("ok", true);
        return ResponseEntity.ok(m);
    }

    /** 版本详情：基本信息、所在渠道、设备数、文件列表、启动包补丁 */
    @GetMapping("/release/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String id, HttpSession session) {
        OtaApp app = shell.current(session);
        Release r = app == null ? null : releases.find(id).filter(x -> x.getAppId().equals(app.getId())).orElse(null);
        if (r == null) return ResponseEntity.status(404).body(result(false, "版本不存在"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("shortId", r.shortId());
        m.put("runtime", r.getRuntimeVersion());
        m.put("platforms", r.getPlatforms());
        m.put("message", r.getMessage());
        m.put("forceUpdate", r.isForceUpdate());
        m.put("size", ui.size(r.getTotalBytes()));
        m.put("source", ui.source(r.getSource()));
        m.put("createdBy", r.getCreatedBy());
        m.put("createdAt", ui.full(r.getCreatedAt()));
        m.put("externalBaseUrl", r.getExternalBaseUrl());
        m.put("republishedFrom", r.getRepublishedFrom() == null ? null : ui.rid(r.getRepublishedFrom()));
        m.put("devices", stats.devicesByUpdate(app.getId()).getOrDefault(r.getId(), 0L));
        List<String> liveIn = new ArrayList<>(), previousIn = new ArrayList<>(), activationIn = new ArrayList<>();
        for (ReleaseViews.Live l : views.live(app)) {
            if (l.getActivation() != null && l.getActivation().getId().equals(r.getId())) activationIn.add(l.getChannel());
            if (l.isRollbackToEmbedded()) continue;
            if (l.getRelease() != null && l.getRelease().getId().equals(r.getId())) {
                liveIn.add(l.getChannel() + (l.getRollout() < 100 ? " · 灰度 " + l.getRollout() + "%" : ""));
            }
            if (l.getPrevious() != null && l.getPrevious().getId().equals(r.getId())) previousIn.add(l.getChannel());
        }
        String lock = releases.inUse(r.getId());
        m.put("liveIn", liveIn);
        m.put("previousIn", previousIn);
        m.put("activationIn", activationIn);
        m.put("inUse", lock != null);
        m.put("lock", lock);

        List<ReleaseFile> files = releases.allFiles(r.getId());
        Map<String, Long> sizes = new HashMap<>();
        for (Asset a : assetRepo.findAllById(files.stream().map(ReleaseFile::getAssetSha256).distinct().toList())) {
            sizes.put(a.getSha256(), a.getSize());
        }
        List<Map<String, Object>> fileViews = new ArrayList<>();
        List<String> launchShas = new ArrayList<>();
        for (ReleaseFile f : files) {
            Map<String, Object> fv = new LinkedHashMap<>();
            fv.put("platform", f.getPlatform());
            fv.put("path", f.getPath());
            fv.put("launch", f.isLaunch());
            fv.put("size", sizes.containsKey(f.getAssetSha256()) ? ui.size(sizes.get(f.getAssetSha256())) : "—");
            fv.put("sha256", f.getAssetSha256());
            fileViews.add(fv);
            if (f.isLaunch()) launchShas.add(f.getAssetSha256());
        }
        m.put("files", fileViews);

        List<Map<String, Object>> patchViews = new ArrayList<>();
        for (Patch p : patchService.forTargets(launchShas)) {
            Map<String, Object> pv = new LinkedHashMap<>();
            pv.put("base", p.getBaseSha().substring(0, 12));
            pv.put("status", switch (p.getStatus()) {
                case Patch.READY -> "可用";
                case Patch.USELESS -> "不比完整文件小多少，不用";
                default -> "生成失败";
            });
            pv.put("size", Patch.READY.equals(p.getStatus()) ? ui.size(p.getPatchSize()) : "—");
            pv.put("ratio", p.getTargetSize() > 0 && Patch.READY.equals(p.getStatus()) ? Math.round(p.getPatchSize() * 100.0 / p.getTargetSize()) + "%" : "—");
            patchViews.add(pv);
        }
        m.put("patches", patchViews);
        List<Release> bases = structure.bases(r);
        m.put("bases", structure.baseOptions(bases));
        m.put("structure", structure.build(r, bases.isEmpty() ? null : bases.get(0)));
        ReleaseStat st = releaseStats.get(r.getId());
        Map<String, Object> funnel = new LinkedHashMap<>();
        funnel.put("offered", st.getOffered());
        funnel.put("installed", st.getInstalled());
        funnel.put("failed", st.getFailed());
        funnel.put("installRate", st.getInstallRate());
        funnel.put("failRate", st.getFailRate());
        funnel.put("alarming", ReleaseStatsService.alarming(st));
        funnel.put("patchDownloads", st.getPatchDownloads());
        funnel.put("fullDownloads", st.getFullDownloads());
        funnel.put("cdnDownloads", st.getCdnDownloads());
        funnel.put("downloads", st.getDownloads());
        funnel.put("downloadRate", st.getOffered() > 0 ? com.ota.platform.common.Percent.of(st.getDownloads(), st.getOffered()) : -1);
        funnel.put("served", ui.bytes(st.getBytesServed()));
        funnel.put("saved", ui.bytes(st.getBytesSaved()));
        long would = st.getBytesServed() + st.getBytesSaved();
        funnel.put("savedPct", would > 0 ? Math.round(st.getBytesSaved() * 100.0 / would) : 0);
        m.put("funnel", funnel);
        List<Map<String, Object>> errs = new ArrayList<>();
        for (LaunchError e : launchErrors.forRelease(r.getId())) {
            Map<String, Object> ev = new LinkedHashMap<>();
            ev.put("at", ui.dts(e.getCreatedAt()));
            ev.put("device", ui.shortId(e.getClientId()));
            ev.put("platform", e.getPlatform());
            ev.put("message", e.getMessage());
            errs.add(ev);
        }
        m.put("errors", errs);
        List<LaunchErrorService.Reason> reasons = launchErrors.reasons(r.getId());
        long total = reasons.stream().mapToLong(LaunchErrorService.Reason::count).sum();
        List<Map<String, Object>> reasonViews = new ArrayList<>();
        for (LaunchErrorService.Reason x : reasons.stream().limit(6).toList()) {
            Map<String, Object> rv = new LinkedHashMap<>();
            rv.put("message", x.message());
            rv.put("count", x.count());
            rv.put("pct", com.ota.platform.common.Percent.of(x.count(), total));
            List<String> plats = new ArrayList<>();
            if (x.ios() > 0) plats.add("iOS " + x.ios());
            if (x.android() > 0) plats.add("Android " + x.android());
            rv.put("platforms", String.join(" · ", plats));
            rv.put("last", ui.ago(x.last()));
            reasonViews.add(rv);
        }
        m.put("reasons", reasonViews);
        m.put("reasonKinds", reasons.size());
        m.put("canRelease", access.can(app, AppPerm.RELEASE));
        m.put("ok", true);
        return ResponseEntity.ok(m);
    }

    /** 改版本说明和强制更新 */
    @PostMapping("/release/edit")
    public String edit(@RequestParam String releaseId, @RequestParam(defaultValue = "") String note,
                       @RequestParam(defaultValue = "false") boolean forceUpdate,
                       HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        try {
            Release r = releases.update(app, releaseId, note, forceUpdate);
            audit.log("RELEASE_UPDATE", app.getSlug() + " 修改版本 " + r.shortId() + "：说明「" + (r.getMessage() == null ? "" : r.getMessage())
                    + "」" + (r.isForceUpdate() ? "，强制更新" : ""));
            ra.addFlashAttribute("msg", "已保存版本 " + r.shortId() + "。之后检查更新的设备拿到新的说明" + (r.isForceUpdate() ? "，下载完会立即重启生效" : ""));
        } catch (Exception e) {
            ra.addFlashAttribute("error", "保存失败：" + reason(e));
        }
        return "redirect:/admin/releases";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam String releaseId, HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        try {
            Release r = releases.delete(app, releaseId);
            audit.log("DELETE", app.getSlug() + " 删除版本 " + r.shortId() + "（Runtime " + r.getRuntimeVersion() + "）");
            ra.addFlashAttribute("msg", "已删除版本 " + r.shortId());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "删除失败：" + reason(e));
        }
        return "redirect:/admin/releases";
    }

    // ==================== 内部 ====================

    /** as：hot（常规更新，默认）、activation（口令更新）；两种都要是 both，或者表单两项都勾上（hot,activation） */
    private static boolean activation(String as) {
        String s = as == null ? "" : as.toLowerCase(java.util.Locale.ROOT);
        return s.contains("activation") || s.contains("both");
    }

    private static boolean hot(String as) {
        String s = as == null ? "" : as.toLowerCase(java.util.Locale.ROOT);
        return !activation(as) || s.contains("hot") || s.contains("both");
    }

    /** 上传后立即发布：按 as 发成常规更新或设成口令更新包，返回提示语的后半句 */
    private String publish(OtaApp app, String channel, Release r, String as) {
        StringBuilder msg = new StringBuilder();
        Release live = hot(as) ? deployHot(app, channel, r, 100, msg) : r;
        if (activation(as)) {
            String done = activate(app, channel, live);
            msg.append(done.isEmpty() ? "，它本来就是 " + channel + " 的口令更新包" : done);
        }
        return msg.toString();
    }

    /** 设成口令更新包并返回提示语的后半句；本来就是时返回空串 */
    private String activate(OtaApp app, String channel, Release r) {
        Channel c = apps.requireChannel(app, channel);
        ReleaseService.Activation a = releases.setActivation(app, c, r.getId(), who());
        if (!a.changed()) return "";
        events.activationDeployed(app, r, c.getName());
        audit.log("ACTIVATION_PACKAGE", app.getSlug() + " · " + c.getName() + " 设口令更新包 " + r.shortId() + "（Runtime " + r.getRuntimeVersion() + "）");
        return "，并已设为 " + c.getName() + " 的口令更新包";
    }

    /** 发布成常规更新，提示语的后半句接在 msg 后面；返回线上实际是哪个版本（复制出来的副本，或者内容相同时原来那个） */
    private Release deployHot(OtaApp app, String channel, Release r, int rollout, StringBuilder msg) {
        Channel c = apps.requireChannel(app, channel);
        ReleaseService.Deployed d = releases.deploy(app, c, r.getId(), rollout, who());
        events.deployed(app, d.release(), c.getName(), rollout, d.republished());
        audit.log("ACTIVATE", app.getSlug() + " · " + c.getName() + " 发布 " + d.release().shortId() + "（Runtime " + r.getRuntimeVersion()
                + "，灰度 " + rollout + "%）" + (d.republished() ? "，比线上版本旧，复制成新版本发布" : ""));
        boolean same = !d.republished() && !d.release().getId().equals(r.getId());
        msg.append("，并已发布到 ").append(c.getName()).append(rollout < 100 ? "（灰度 " + rollout + "%）" : "")
                .append(d.republished() ? "。它比线上版本旧，App 不会接受旧版本，所以复制成了新版本 " + d.release().shortId() + " 发布" : "")
                .append(same ? "。线上本来就是这个版本的内容（" + d.release().shortId() + "），没有再复制" : "");
        return d.release();
    }

    /** 发布弹窗打开时先问一下：这次是正常发布、只调灰度（线上已经是这份内容），还是要复制成新版本；带上线上版本和两边的创建时间 */
    @GetMapping("/deploy/plan")
    @ResponseBody
    public Map<String, Object> deployPlan(@RequestParam String releaseId, @RequestParam(defaultValue = "production") String channel,
                                          HttpSession session) {
        OtaApp app = shell.current(session);
        if (app == null) return result(false, "还没有 App");
        try {
            Release target = releases.requireRelease(app, releaseId);
            ReleaseService.Plan p = releases.plan(app, apps.requireChannel(app, channel), releaseId);
            java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("MM-dd HH:mm:ss").withZone(java.time.ZoneId.of(ui.zoneId()));
            Map<String, Object> m = result(true, "");
            m.put("mode", p.mode());
            m.put("at", f.format(target.getCreatedAt()));
            if (p.live() != null) {
                m.put("live", p.live().shortId());
                m.put("liveAt", f.format(p.live().getCreatedAt()));
                m.put("liveCopy", p.live().getRepublishedFrom() != null);
            }
            return m;
        } catch (Exception e) {
            return result(false, reason(e));
        }
    }

    /** 记在版本和发布记录上的操作人：后台显示管理员的名字 */
    private String who() {
        CurrentAdmin me = access.current();
        return me == null ? "后台" : me.name();
    }

    private static Map<String, Object> result(boolean ok, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("msg", msg);
        return m;
    }

    private static String reason(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
