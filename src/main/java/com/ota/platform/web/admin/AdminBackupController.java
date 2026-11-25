package com.ota.platform.web.admin;

import com.ota.platform.web.AdminUi;
import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AdminUserService;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.audit.AuditService;
import com.ota.platform.backup.ProjectBackupService;
import com.ota.platform.backup.ProjectCrypto;
import com.ota.platform.common.ApiException;
import com.ota.platform.install.Restarter;
import com.ota.platform.installer.ChunkedUploads;
import com.ota.platform.settings.SettingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 系统配置 → 备份：完整项目备份的下载、自动备份、从备份恢复（超级管理员） */
@Controller
@RequestMapping("/admin/settings/backup")
public class AdminBackupController {

    private static final Logger log = LoggerFactory.getLogger(AdminBackupController.class);
    private static final String BACK = "redirect:/admin/settings#backup";
    private static final String BACK_URL = "/admin/settings#backup";
    private static final long MAX_UPLOAD = 8L * 1024 * 1024 * 1024;
    /** 打包好以后，下载链接的有效期 */
    private static final long LINK_TTL = 10 * 60_000;

    private final ProjectBackupService backups;
    private final AccessService access;
    private final AdminUserService adminUsers;
    private final AuditService audit;
    private final SettingService settings;
    private final Restarter restarter;
    private final ChunkedUploads chunks;
    private final AdminUi ui;
    private final boolean demo;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Job> jobs = new ConcurrentHashMap<>();
    /** 一次只打包一份，同时点了几次就排队，不抢磁盘 */
    private final ExecutorService packer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "ota-backup-pack");
        t.setDaemon(true);
        return t;
    });

    public AdminBackupController(ProjectBackupService backups, AccessService access, AdminUserService adminUsers, AuditService audit,
                                 SettingService settings, Restarter restarter, ChunkedUploads chunks, AdminUi ui,
                                 @Value("${ota.demo.mode:false}") boolean demo) {
        this.backups = backups;
        this.access = access;
        this.adminUsers = adminUsers;
        this.audit = audit;
        this.settings = settings;
        this.restarter = restarter;
        this.chunks = chunks;
        this.ui = ui;
        this.demo = demo;
    }

    private boolean passwordOk(String password) {
        CurrentAdmin me = access.current();
        return me != null && password != null && adminUsers.passwordMatches(me.id(), password);
    }

    private static String newPasswordError(String p1, String p2) {
        if (p1 == null || p1.length() < 8) return "备份密码至少 8 位";
        if (!p1.equals(p2)) return "两次输入的备份密码不一样";
        return null;
    }

    private String fileName() {
        return "ota-backup-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmm").format(LocalDateTime.now(ZoneId.of(ui.zoneId()))) + ".otaproj";
    }

    /**
     * 一次下载：先在后台把加密好的备份完整写成文件（页面轮询进度），写完才给下载链接，按文件实际大小发。
     * 打包出错直接告诉页面，不会边打包边下载、出错时留下一个看着正常的残缺文件。链接只给发起的管理员用。
     */
    private static final class Job {
        final long adminId;
        final String fileName;
        final String what;
        volatile boolean temp;
        volatile Path file;
        volatile long written;
        volatile String error;
        /** 存到服务器上的备份没有下载链接，做完了这里是给页面的提示 */
        volatile String done;
        volatile long exp = Long.MAX_VALUE;

        Job(long adminId, String fileName, String what) {
            this.adminId = adminId;
            this.fileName = fileName;
            this.what = what;
        }
    }

    private interface Pack {
        Path run(Job job) throws IOException;
    }

    private void purge() {
        long now = System.currentTimeMillis();
        jobs.entrySet().removeIf(e -> {
            if (e.getValue().exp > now) return false;
            drop(e.getValue());
            return true;
        });
    }

    private static void drop(Job job) {
        if (!job.temp || job.file == null) return;
        try {
            Files.deleteIfExists(job.file);
        } catch (IOException ignored) {
            // 临时目录每小时清理一次
        }
    }

    /** 脚本发来的就放到后台打包、马上返回；浏览器直接提交的表单就在这次请求里打包完 */
    private String start(Job job, boolean temp, Pack pack, boolean background) {
        purge();
        byte[] buf = new byte[24];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        jobs.put(token, job);
        Runnable run = () -> {
            try {
                Path f = pack.run(job);
                job.temp = temp;
                job.file = f;
            } catch (Exception e) {
                log.error("打包备份失败", e);
                job.error = "打包失败：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            }
            job.exp = System.currentTimeMillis() + LINK_TTL;
        };
        if (background) packer.execute(run);
        else run.run();
        return token;
    }

    private Job mine(String token) {
        purge();
        CurrentAdmin me = access.current();
        Job job = jobs.get(token);
        return job == null || me == null || job.adminId != me.id() ? null : job;
    }

    private static String base(String token) {
        return "/admin/settings/backup/once/" + token;
    }

    private boolean ajax(HttpServletRequest req) {
        String x = req.getHeader("X-Requested-With");
        return x != null && x.equalsIgnoreCase("XMLHttpRequest");
    }

    private Object fail(HttpServletRequest req, HttpServletResponse res, String error) {
        if (ajax(req)) return result(false, error);
        back(req, res, error);
        return null;
    }

    private static void back(HttpServletRequest req, HttpServletResponse res, String error) {
        RequestContextUtils.getOutputFlashMap(req).put("error", error);
        RequestContextUtils.saveOutputFlashMap("/admin/settings", req, res);
        res.setStatus(HttpServletResponse.SC_FOUND);
        res.setHeader(HttpHeaders.LOCATION, BACK_URL);
    }

    private Object started(HttpServletRequest req, HttpServletResponse res, String token) {
        if (ajax(req)) {
            Map<String, Object> r = result(true, "");
            r.put("status", base(token) + "/status");
            return r;
        }
        Job job = jobs.get(token);
        if (job == null || job.error != null) return fail(req, res, job == null ? "下载链接已失效，请重新下载" : job.error);
        res.setStatus(HttpServletResponse.SC_FOUND);
        res.setHeader(HttpHeaders.LOCATION, base(token) + "/download");
        return null;
    }

    /** 下载完整备份：校验密码后在后台打包 */
    @PostMapping("/download")
    @ResponseBody
    public Object download(@RequestParam String adminPassword, @RequestParam String backupPassword,
                           @RequestParam String backupPassword2, HttpServletRequest req, HttpServletResponse res) {
        String error = passwordOk(adminPassword) ? newPasswordError(backupPassword, backupPassword2) : "登录密码不对，没有生成备份";
        if (error != null) return fail(req, res, error);
        Job job = new Job(access.current().id(), fileName(), "下载完整项目备份");
        return started(req, res, start(job, true, j -> backups.prepare(backupPassword, n -> j.written = n), ajax(req)));
    }

    /** 打包进度：packing 还在打包；ready 带下载链接和实际大小 */
    @GetMapping("/once/{token}/status")
    @ResponseBody
    public Map<String, Object> status(@PathVariable String token) {
        Job job = mine(token);
        if (job == null) return result(false, "下载链接已失效，请重新下载");
        if (job.error != null) {
            jobs.remove(token, job);
            return result(false, job.error);
        }
        Map<String, Object> r = result(true, "");
        if (job.done != null) {
            jobs.remove(token, job);
            r.put("state", "ready");
            r.put("msg", job.done);
            return r;
        }
        Path f = job.file;
        if (f == null) {
            r.put("state", "packing");
            r.put("written", job.written > 0 ? mb(job.written) : "");
            return r;
        }
        long size = f.toFile().length();
        r.put("state", "ready");
        r.put("url", base(token) + "/download");
        r.put("msg", "备份已打包好（" + mb(size) + "），开始下载");
        return r;
    }

    /** 下载打包好的文件：带 Content-Length，传完删掉临时文件；中途断了，链接有效期内可以重试 */
    @GetMapping("/once/{token}/download")
    public void once(@PathVariable String token, HttpServletRequest req, HttpServletResponse res) throws IOException {
        Job job = mine(token);
        Path f = job == null ? null : job.file;
        if (f == null || !Files.isRegularFile(f)) {
            back(req, res, "下载链接已失效，请重新下载");
            return;
        }
        long size = Files.size(f);
        audit.log("PROJECT_BACKUP", job.what + "（" + mb(size) + "）");
        res.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        res.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + job.fileName + "\"");
        res.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        res.setContentLengthLong(size);
        job.exp = Long.MAX_VALUE;
        try (OutputStream out = res.getOutputStream()) {
            Files.copy(f, out);
        } catch (IOException e) {
            job.exp = System.currentTimeMillis() + LINK_TTL;
            throw e;
        }
        if (jobs.remove(token, job)) drop(job);
    }

    static String mb(long bytes) {
        if (bytes >= 1L << 30) return String.format("%.1f GB", bytes / (double) (1L << 30));
        if (bytes >= 1L << 20) return String.format("%.1f MB", bytes / (double) (1L << 20));
        return Math.max(1, bytes / 1024) + " KB";
    }

    @PostMapping("/auto")
    public String auto(@RequestParam(defaultValue = "false") boolean enabled, @RequestParam(defaultValue = "7") int keep,
                       @RequestParam(defaultValue = "3") int hour, RedirectAttributes ra) {
        int k = Math.max(1, Math.min(30, keep)), h = Math.max(0, Math.min(23, hour));
        settings.set(ProjectBackupService.AUTO, String.valueOf(enabled));
        settings.set(ProjectBackupService.KEEP, String.valueOf(k));
        settings.set(ProjectBackupService.HOUR, String.valueOf(h));
        audit.log("PROJECT_BACKUP", enabled ? "开启自动备份：每天 " + h + " 点，保留 " + k + " 份" : "关闭了自动备份");
        ra.addFlashAttribute("msg", enabled ? "已开启自动备份：每天 " + h + " 点备份一次，保留最近 " + k + " 份" : "已关闭自动备份");
        return BACK;
    }

    @PostMapping("/now")
    public String now(RedirectAttributes ra) {
        try {
            ProjectBackupService.Stored s = backups.backupNow();
            audit.log("PROJECT_BACKUP", "在服务器上备份了一份：" + s.name() + "（" + mb(s.size()) + "）");
            ra.addFlashAttribute("msg", "已备份：" + s.name() + "（" + mb(s.size()) + "），存在服务器的 data/backups 下");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "备份失败：" + e.getMessage());
        }
        return BACK;
    }

    /** 页面上点的：放到后台备份，按钮上显示进度。数据多的时候要好一会儿，反向代理等不了那么久 */
    @PostMapping(value = "/now", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public Map<String, Object> nowInBackground() {
        String by = AuditService.operator();
        Job job = new Job(access.current().id(), null, null);
        String token = start(job, false, j -> {
            ProjectBackupService.Stored s = backups.backupNow(n -> j.written = n);
            audit.logAs(by, null, "PROJECT_BACKUP", "在服务器上备份了一份：" + s.name() + "（" + mb(s.size()) + "）");
            j.done = "已备份：" + s.name() + "（" + mb(s.size()) + "），存在服务器的 data/backups 下";
            return null;
        }, true);
        Map<String, Object> r = result(true, "");
        r.put("status", base(token) + "/status");
        return r;
    }

    /** 下载服务器上的一份：.otaproj 本来就加密了，原样下；.zip 先用备份密码加密成临时文件 */
    @PostMapping("/stored/{name}/download")
    @ResponseBody
    public Object storedDownload(@PathVariable String name, @RequestParam String adminPassword,
                                 @RequestParam(defaultValue = "") String backupPassword,
                                 @RequestParam(defaultValue = "") String backupPassword2,
                                 HttpServletRequest req, HttpServletResponse res) {
        try {
            backups.storedFile(name);
        } catch (IOException e) {
            return fail(req, res, "备份文件不存在");
        }
        boolean encrypted = name.endsWith(".otaproj");
        String error = !passwordOk(adminPassword) ? "登录密码不对，没有下载" : encrypted ? null : newPasswordError(backupPassword, backupPassword2);
        if (error != null) return fail(req, res, error);
        Job job = new Job(access.current().id(), encrypted ? name : name.replaceAll("\\.zip$", ".otaproj"), "下载服务器上的备份 " + name);
        Pack pack = encrypted ? j -> backups.storedFile(name) : j -> backups.encryptStored(name, backupPassword, n -> j.written = n);
        return started(req, res, start(job, !encrypted, pack, ajax(req)));
    }

    @PostMapping("/stored/{name}/delete")
    public String storedDelete(@PathVariable String name, RedirectAttributes ra) {
        try {
            backups.delete(name);
            audit.log("PROJECT_BACKUP", "删除服务器上的备份 " + name);
            ra.addFlashAttribute("msg", "已删除 " + name);
        } catch (IOException e) {
            ra.addFlashAttribute("error", "删除失败：" + e.getMessage());
        }
        return BACK;
    }

    @PostMapping("/stored/{name}/restore")
    public String storedRestore(@PathVariable String name, @RequestParam String adminPassword,
                                @RequestParam(defaultValue = "") String backupPassword, RedirectAttributes ra) {
        if (demo) {
            ra.addFlashAttribute("error", "演示环境不能恢复整个项目");
            return BACK;
        }
        if (!passwordOk(adminPassword)) {
            ra.addFlashAttribute("error", "登录密码不对，没有恢复");
            return BACK;
        }
        try {
            ProjectBackupService.Pending p = backups.stageStored(name, backupPassword, AuditService.operator());
            restart(p, name);
            ra.addFlashAttribute("msg", "正在从 " + name + " 恢复，服务马上重启，等十几秒刷新页面重新登录");
        } catch (ProjectCrypto.BadPassword e) {
            ra.addFlashAttribute("error", e.getMessage());
        } catch (IOException e) {
            ra.addFlashAttribute("error", "没有恢复：" + e.getMessage());
        }
        return BACK;
    }

    private void restart(ProjectBackupService.Pending p, String from) {
        audit.log("PROJECT_RESTORE", "从项目备份恢复（" + from + "，备份时间 " + p.createdAt() + "），重启服务替换数据");
        restarter.restartSoon();
    }

    // ===== 上传备份文件恢复：分片上传，绕开反向代理对单个请求的大小限制 =====

    @PostMapping("/restore/start")
    @ResponseBody
    public Map<String, Object> restoreStart(@RequestParam String fileName, @RequestParam long size) {
        if (demo) return result(false, "演示环境不能恢复整个项目");
        try {
            Map<String, Object> r = result(true, "");
            r.put("id", chunks.start(AuditService.operator(), 0L, fileName, size, "otaproj", "选项目备份文件（.otaproj）", MAX_UPLOAD, "备份文件最大 8GB"));
            return r;
        } catch (ApiException e) {
            return result(false, e.getMessage());
        } catch (Exception e) {
            return result(false, "上传失败：" + e.getMessage());
        }
    }

    @PostMapping(value = "/restore/{id}", consumes = org.springframework.http.MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @ResponseBody
    public Map<String, Object> restoreAppend(@PathVariable String id, @RequestParam long offset, HttpServletRequest req) {
        try {
            Map<String, Object> r = result(true, "");
            r.put("received", chunks.append(id, AuditService.operator(), 0L, offset, req.getInputStream()));
            return r;
        } catch (ApiException e) {
            return result(false, e.getMessage());
        } catch (Exception e) {
            return result(false, "上传失败：" + e.getMessage());
        }
    }

    @PostMapping("/restore/{id}/finish")
    @ResponseBody
    public Map<String, Object> restoreFinish(@PathVariable String id, @RequestParam String adminPassword, @RequestParam String backupPassword) {
        if (demo) return result(false, "演示环境不能恢复整个项目");
        if (!passwordOk(adminPassword)) return result(false, "登录密码不对，没有恢复");
        ChunkedUploads.Done done;
        try {
            done = chunks.finish(id, AuditService.operator(), 0L);
        } catch (ApiException e) {
            return result(false, e.getMessage());
        }
        String by = AuditService.operator();
        return check(done.fileName(), done.file(), () -> backups.stageEncrypted(done.file(), backupPassword, by));
    }

    // ===== 解密、检查备份放到后台做：大文件要一会儿，反向代理（宝塔默认 60 秒）等不了。页面轮询检查结果，通过了服务自己重启 =====

    private interface Stage {
        ProjectBackupService.Pending run() throws IOException;
    }

    private static final class Check {
        final long adminId;
        final long at = System.currentTimeMillis();
        volatile boolean passed;
        volatile String error;

        Check(long adminId) {
            this.adminId = adminId;
        }
    }

    private final Map<String, Check> checks = new ConcurrentHashMap<>();

    /** temp：检查完要删掉的上传临时文件，服务器上的备份传 null */
    private synchronized Map<String, Object> check(String from, Path temp, Stage stage) {
        checks.values().removeIf(c -> System.currentTimeMillis() - c.at > 3_600_000L);
        if (checks.values().stream().anyMatch(c -> !c.passed && c.error == null)) {
            deleteQuietly(temp);
            return result(false, "已经有一份备份在检查了，等它检查完再试");
        }
        String by = AuditService.operator();
        Check c = new Check(access.current().id());
        byte[] buf = new byte[18];
        random.nextBytes(buf);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
        checks.put(token, c);
        Thread t = new Thread(() -> {
            try {
                ProjectBackupService.Pending p = stage.run();
                audit.logAs(by, null, "PROJECT_RESTORE", "从项目备份恢复（" + from + "，备份时间 " + p.createdAt() + "），重启服务替换数据");
                c.passed = true;
                restarter.restartSoon();
            } catch (ProjectCrypto.BadPassword e) {
                c.error = e.getMessage();
            } catch (ApiException | IOException e) {
                c.error = "没有恢复：" + e.getMessage();
            } catch (RuntimeException e) {
                log.error("检查备份失败", e);
                c.error = "没有恢复：" + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            } finally {
                deleteQuietly(temp);
            }
        }, "ota-restore-check");
        t.setDaemon(true);
        t.start();
        Map<String, Object> r = result(true, "");
        r.put("check", "/admin/settings/backup/restore/check/" + token);
        return r;
    }

    private static void deleteQuietly(Path f) {
        if (f == null) return;
        try {
            Files.deleteIfExists(f);
        } catch (IOException ignored) {
            // 临时文件下次清理
        }
    }

    /** checking 还在检查；restarting 检查通过，服务马上重启（boot 是重启前这次启动的时间，页面用它判断重启好了没有） */
    @GetMapping("/restore/check/{token}")
    @ResponseBody
    public Map<String, Object> restoreCheck(@PathVariable String token) {
        Check c = checks.get(token);
        CurrentAdmin me = access.current();
        if (c == null || me == null || c.adminId != me.id()) return result(false, "找不到这次恢复的检查结果，刷新页面看看");
        if (c.error != null) {
            checks.remove(token, c);
            return result(false, c.error);
        }
        Map<String, Object> r = result(true, "");
        r.put("state", c.passed ? "restarting" : "checking");
        r.put("boot", restarter.boot());
        return r;
    }

    @PostMapping(value = "/stored/{name}/restore", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public Map<String, Object> storedRestoreInBackground(@PathVariable String name, @RequestParam String adminPassword,
                                                         @RequestParam(defaultValue = "") String backupPassword) {
        if (demo) return result(false, "演示环境不能恢复整个项目");
        if (!passwordOk(adminPassword)) return result(false, "登录密码不对，没有恢复");
        try {
            backups.storedFile(name);
        } catch (IOException e) {
            return result(false, "备份文件不存在");
        }
        String by = AuditService.operator();
        return check(name, null, () -> backups.stageStored(name, backupPassword, by));
    }

    @PostMapping(value = "/restore/apply", headers = "X-Requested-With=XMLHttpRequest")
    @ResponseBody
    public Map<String, Object> restoreApplyNow(@RequestParam String adminPassword) {
        if (demo) return result(false, "演示环境不能恢复整个项目");
        ProjectBackupService.Pending p = backups.pending();
        if (p == null) return result(false, "没有准备好的恢复");
        if (!passwordOk(adminPassword)) return result(false, "登录密码不对，没有重启");
        long boot = restarter.boot();
        restart(p, "准备好的备份");
        Map<String, Object> r = result(true, "服务马上重启");
        r.put("boot", boot);
        return r;
    }

    @PostMapping("/restore/cancel")
    public String restoreCancel(RedirectAttributes ra) throws IOException {
        backups.cancelPending();
        audit.log("PROJECT_RESTORE", "取消了准备好的项目恢复");
        ra.addFlashAttribute("msg", "已取消恢复，数据不变");
        return BACK;
    }

    @PostMapping("/restore/apply")
    public String restoreApply(@RequestParam String adminPassword, RedirectAttributes ra) {
        if (demo) {
            ra.addFlashAttribute("error", "演示环境不能恢复整个项目");
            return BACK;
        }
        ProjectBackupService.Pending p = backups.pending();
        if (p == null) {
            ra.addFlashAttribute("error", "没有准备好的恢复");
            return BACK;
        }
        if (!passwordOk(adminPassword)) {
            ra.addFlashAttribute("error", "登录密码不对，没有重启");
            return BACK;
        }
        restart(p, "准备好的备份");
        ra.addFlashAttribute("msg", "服务马上重启，等十几秒刷新页面重新登录");
        return BACK;
    }

    private static Map<String, Object> result(boolean ok, String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("ok", ok);
        m.put("msg", msg);
        return m;
    }
}
