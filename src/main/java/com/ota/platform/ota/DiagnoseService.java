package com.ota.platform.ota;

import com.ota.platform.app.AppService;
import com.ota.platform.app.Channel;
import com.ota.platform.app.OtaApp;
import com.ota.platform.release.Deployment;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseFile;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.signing.SigningInspector;
import com.ota.platform.stats.DeviceSeen;
import com.ota.platform.stats.StatsService;
import com.ota.platform.web.AdminUi;
import org.springframework.stereotype.Service;

import java.util.*;

/** 模拟一次检查更新：按清单接口同样的逻辑走一遍，说清楚这台设备会拿到什么、为什么 */
@Service
public class DiagnoseService {

    private final AppService apps;
    private final ReleaseService releases;
    private final StatsService stats;
    private final TestDeviceService testDevices;
    private final SigningInspector signing;
    private final AdminUi ui;

    public DiagnoseService(AppService apps, ReleaseService releases, StatsService stats, TestDeviceService testDevices,
                           SigningInspector signing, AdminUi ui) {
        this.apps = apps;
        this.releases = releases;
        this.stats = stats;
        this.testDevices = testDevices;
        this.signing = signing;
        this.ui = ui;
    }

    public record Step(String label, String state, String text) {}

    /** level：ok 会收到更新或已是最新，warn 没有更新，error 有问题 */
    public record Result(String verdict, String level, String releaseId, List<Step> steps) {}

    public Result diagnose(OtaApp app, String clientId, String platform, String runtime, String channel) {
        List<Step> steps = new ArrayList<>();
        String cid = clientId == null || clientId.isBlank() ? null : clientId.trim();
        String plat = "android".equalsIgnoreCase(platform) ? "android" : "ios";
        String rv = runtime == null || runtime.isBlank() ? "1.0.0" : runtime.trim();
        String requested = channel == null || channel.isBlank() ? AppService.DEFAULT_CHANNEL : channel.trim();

        DeviceSeen dev = cid == null ? null : stats.device(app.getId(), cid).orElse(null);
        String current = dev == null ? null : dev.getCurrentUpdateId();
        if (dev != null) {
            steps.add(new Step("设备", "info", ui.ago(dev.getLastSeen()) + "检查过更新，当时运行" + versionLabel(current)
                    + "，渠道 " + Objects.toString(dev.getChannel(), "production") + "，Runtime " + Objects.toString(dev.getRuntimeVersion(), "—")));
        } else if (cid != null) {
            steps.add(new Step("设备", "warn", "没有这台设备检查更新的记录。设备 ID 是 App 里 expo-updates 生成的 eas-client-id"));
        } else {
            steps.add(new Step("设备", "info", "没填设备 ID，按没有设备 ID 的请求模拟（不进灰度）"));
        }

        String effective = requested;
        Optional<String> tester = testDevices.channelFor(app, cid);
        boolean first = testDevices.rolloutFirst(app, cid);
        if (tester.isPresent()) {
            effective = tester.get();
            steps.add(new Step("测试设备", "info", "这台设备在测试设备名单里，" + (effective.equals(requested) ? "用渠道 " + effective
                    : "改用渠道 " + effective + "（App 里写的是 " + requested + "）") + (first ? "，在灰度白名单里" : "")));
        }
        steps.add(new Step("请求", "info", (plat.equals("ios") ? "iOS" : "Android") + " · Runtime " + rv + " · 渠道 " + effective));

        SigningInspector.Status sig = signing.inspect(app);
        boolean badSigning = "error".equals(sig.getLevel());
        if (badSigning) steps.add(new Step("签名", "error", sig.getHeadline() + "。App 会拒绝这个服务器返回的所有更新"));

        Optional<Channel> ch = apps.findChannel(app, effective);
        if (ch.isEmpty()) {
            steps.add(new Step("渠道", "error", "渠道 " + effective + " 不存在，服务器返回「没有更新」"));
            return new Result("没有更新", "warn", null, steps);
        }
        Deployment d = releases.deployments(ch.get()).stream().filter(x -> x.getRuntimeVersion().equals(rv)).findFirst().orElse(null);
        if (d == null) {
            steps.add(new Step("发布", "warn", "渠道 " + effective + " 还没有发布过 Runtime " + rv + " 的版本。Runtime 要和安装包一致"));
            return new Result("没有更新", "warn", null, steps);
        }
        boolean fresh = current == null || StatsService.EMBEDDED.equals(current);
        Release act = releases.find(d.getActivationReleaseId()).orElse(null);
        String id = null;
        boolean viaActivation = false;
        if (act != null && fresh) {
            if (releases.files(act.getId(), plat).stream().anyMatch(ReleaseFile::isLaunch)) {
                id = act.getId();
                viaActivation = true;
                steps.add(new Step("口令更新", "ok", "设备还在用安装包内置版本（新装的、正在输入口令激活的），渠道 " + effective
                        + " 单独设了口令更新包 " + act.shortId() + "，直接拿它，不看灰度和回退内置版本"));
            } else {
                steps.add(new Step("口令更新", "info", "口令更新包 " + act.shortId() + " 没有 " + (plat.equals("ios") ? "iOS" : "Android")
                        + " 的文件，照旧拿常规更新包"));
            }
        } else if (act != null) {
            steps.add(new Step("常规更新", "info", "渠道 " + effective + " 单独设了口令更新包 " + act.shortId()
                    + "，只给新装 App 输入口令激活时用；这台设备已经激活过，拿常规更新包"));
        }
        if (!viaActivation && d.isRollbackToEmbedded()) {
            steps.add(new Step("发布", "warn", "这个渠道已回退到安装包内置版本（" + ui.dt(d.getRollbackAt()) + "），设备会丢掉热更新"));
            return new Result("回到安装包内置版本", "warn", null, steps);
        }
        if (!viaActivation) id = d.getReleaseId();
        if (id == null) {
            steps.add(new Step("发布", "warn", "这个渠道没有线上版本"));
            return new Result("没有更新", "warn", null, steps);
        }
        if (!viaActivation && d.getRolloutPercent() < 100) {
            String prev = d.getPreviousReleaseId();
            if (prev != null && releases.find(prev).isEmpty()) prev = null;
            String keep = "，设备还在用安装包内置版本（新装的、正在输入口令激活的）照样拿线上版本，灰度不会让口令激活失败";
            if (first) {
                steps.add(new Step("灰度", "ok", "线上版本 " + d.getReleaseId().substring(0, 8) + " 灰度 " + d.getRolloutPercent()
                        + "%，这台设备在灰度白名单里，不管灰度比例都先拿线上版本 → 在灰度里"));
            } else if (cid == null) {
                steps.add(new Step("灰度", "info", "线上版本 " + id.substring(0, 8) + " 灰度 " + d.getRolloutPercent() + "%，没有设备 ID 的请求不进灰度，"
                        + (prev != null ? "拿上一版本" : "没有上一版本" + keep)));
                if (prev != null) id = prev;
            } else {
                int bucket = ReleaseService.bucket(cid, d.getReleaseId());
                boolean in = bucket < d.getRolloutPercent();
                String out = prev != null ? "不在灰度里，拿上一版本 " + prev.substring(0, 8) : fresh ? "不在灰度里，没有上一版本" + keep : "不在灰度里，没有上一版本";
                steps.add(new Step("灰度", in ? "ok" : "info", "线上版本 " + d.getReleaseId().substring(0, 8) + " 灰度 " + d.getRolloutPercent()
                        + "%，这台设备的分组号是 " + bucket + "（0–99，小于 " + d.getRolloutPercent() + " 才在灰度里）→ " + (in ? "在灰度里" : out)));
                if (!in && (prev != null || !fresh)) id = prev;
            }
            if (id == null) return new Result("没有更新", "warn", null, steps);
        }
        Release r = releases.find(id).orElse(null);
        if (r == null) {
            steps.add(new Step("版本", "error", "版本 " + id.substring(0, 8) + " 已经不存在"));
            return new Result("没有更新", "warn", null, steps);
        }
        steps.add(new Step("版本", "ok", "选中版本 " + r.shortId() + (r.getMessage() != null ? "（" + r.getMessage() + "）" : "")
                + "，" + ui.dt(r.getCreatedAt()) + " 发布" + (r.isForceUpdate() ? "，强制更新" : "")));
        List<ReleaseFile> files = releases.files(r.getId(), plat);
        if (files.stream().noneMatch(ReleaseFile::isLaunch)) {
            steps.add(new Step("平台", "error", "这个版本没有 " + (plat.equals("ios") ? "iOS" : "Android") + " 的文件，服务器返回「没有更新」"));
            return new Result("没有更新", "warn", r.getId(), steps);
        }
        if (dev != null && dev.getFailedUpdateIds() != null && dev.getFailedUpdateIds().contains(r.getId())) {
            steps.add(new Step("启动失败", "warn", "这台设备报告过这个版本启动失败，expo-updates 不会再启动它，会继续用之前的版本"));
        }
        if (r.getId().equalsIgnoreCase(Objects.toString(current, ""))) {
            steps.add(new Step("结果", "ok", "设备已经在运行这个版本，不会重复下载"));
            return new Result("已经是最新版本", badSigning ? "error" : "ok", r.getId(), steps);
        }
        steps.add(new Step("结果", badSigning ? "error" : "ok", "服务器会返回签名后的清单，设备下载完" + (r.isForceUpdate() ? "立即重启生效" : "下次启动生效")));
        return new Result("会收到版本 " + r.shortId(), badSigning ? "error" : "ok", r.getId(), steps);
    }

    private String versionLabel(String id) {
        if (id == null) return "未上报的版本";
        if (StatsService.EMBEDDED.equals(id)) return "安装包内置版本";
        return releases.find(id).map(r -> "版本 " + r.shortId()).orElse("版本 " + (id.length() > 8 ? id.substring(0, 8) : id) + "（服务器上已没有）");
    }
}
