package com.ota.platform.ota;

import com.ota.platform.admin.AdminUser;
import com.ota.platform.admin.AdminUserRepo;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.release.Release;
import com.ota.platform.webhook.WebhookService;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/** 发布、回滚、切换、启动失败这些事件：发 Webhook（给你的服务器）和群机器人消息（给人看） */
@Service
public class OtaEvents {

    private final WebhookService webhooks;
    private final NotifyService notify;
    private final AdminUserRepo adminUsers;

    public OtaEvents(WebhookService webhooks, NotifyService notify, AdminUserRepo adminUsers) {
        this.webhooks = webhooks;
        this.notify = notify;
        this.adminUsers = adminUsers;
    }

    public void deployed(OtaApp app, Release r, String channel, int rollout, boolean republished) {
        String by = who();
        Map<String, Object> data = base(app, operator());
        data.put("channel", channel);
        data.put("release", release(r));
        data.put("rolloutPercent", rollout);
        data.put("republished", republished);
        webhooks.emit(app, "release.deployed", data);
        notify.send(app, "publish", by + " 把版本 " + r.shortId() + note(r) + " 发布到 " + channel
                + (rollout < 100 ? "，灰度 " + rollout + "%" : "") + "（Runtime " + r.getRuntimeVersion() + "）");
    }

    public void activationDeployed(OtaApp app, Release r, String channel) {
        String by = who();
        Map<String, Object> data = base(app, operator());
        data.put("channel", channel);
        data.put("release", release(r));
        webhooks.emit(app, "release.activation_deployed", data);
        notify.send(app, "publish", by + " 把版本 " + r.shortId() + note(r) + " 设为 " + channel + " 的口令更新包（Runtime "
                + r.getRuntimeVersion() + "），新装 App 输入口令激活时下载它");
    }

    public void activationCleared(OtaApp app, String channel, String runtime) {
        String by = who();
        Map<String, Object> data = base(app, operator());
        data.put("channel", channel);
        data.put("runtimeVersion", runtime);
        webhooks.emit(app, "release.activation_cleared", data);
        notify.send(app, "publish", by + " 取消了 " + channel + " 的口令更新包（Runtime " + runtime + "），输入口令激活时改回拿常规更新包");
    }

    public void rolledBack(OtaApp app, Release r, String channel) {
        String by = who();
        Map<String, Object> data = base(app, operator());
        data.put("channel", channel);
        data.put("release", release(r));
        webhooks.emit(app, "release.rolledback", data);
        String from = r.getRepublishedFrom() != null ? r.getRepublishedFrom().substring(0, 8) : r.shortId();
        notify.send(app, "rollback", by + " 把 " + channel + " 回滚到版本 " + from + note(r) + "（Runtime " + r.getRuntimeVersion() + "）");
    }

    public void rolledBackToEmbedded(OtaApp app, String channel, String runtime) {
        String by = who();
        Map<String, Object> data = base(app, operator());
        data.put("channel", channel);
        data.put("runtimeVersion", runtime);
        webhooks.emit(app, "release.embedded", data);
        notify.send(app, "embedded", by + " 让 " + channel + "（Runtime " + runtime + "）的设备回到安装包内置版本");
    }

    public void modeSwitched(OtaApp app, boolean open) {
        String by = who();
        Map<String, Object> data = base(app, operator());
        data.put("redemptionOpen", open);
        webhooks.emit(app, "mode.switched", data);
        notify.send(app, "switch", by + " 切换到 " + (open ? "B 面（上线模式）" : "A 面（审核模式）"));
    }

    public void launchAlert(OtaApp app, Release r, ReleaseStat s) {
        Map<String, Object> data = base(app, null);
        data.put("release", release(r));
        data.put("failed", s.getFailed());
        data.put("offered", s.getOffered());
        data.put("failRate", s.getFailRate());
        webhooks.emit(app, "release.launch_failures", data);
        notify.send(app, "alert", "⚠ 版本 " + r.shortId() + note(r) + " 有 " + s.getFailed() + " 台设备启动失败（失败率 " + s.getFailRate()
                + "%），expo-updates 已让它们退回之前的版本。建议到后台检查后回滚");
    }

    /** 启动失败太多，自动回滚了：failed 是出问题的版本，to 是回滚后发布的版本（旧版本复制出来的） */
    public void autoRolledBack(OtaApp app, Release failed, Release to, String channel, ReleaseStat s, int rate, int min) {
        Map<String, Object> data = base(app, ReleaseGuard.OPERATOR);
        data.put("channel", channel);
        data.put("release", release(failed));
        data.put("rolledBackTo", release(to));
        data.put("failed", s.getFailed());
        data.put("offered", s.getOffered());
        data.put("failRate", s.getFailRate());
        data.put("threshold", Map.of("failRate", rate, "failed", min));
        webhooks.emit(app, "release.auto_rollback", data);
        String toId = to.getRepublishedFrom() != null ? to.getRepublishedFrom().substring(0, 8) : to.shortId();
        notify.send(app, "alert", "⚠ 已自动回滚：版本 " + failed.shortId() + note(failed) + " 在 " + channel + " 有 " + s.getFailed()
                + " 台设备启动失败（失败率 " + s.getFailRate() + "%），达到设定的 " + rate + "%，已回滚到版本 " + toId
                + "（Runtime " + failed.getRuntimeVersion() + "）");
    }

    /** 开了自动回滚、失败也到了设定值，但没有能回滚过去的版本（why 说明原因） */
    public void autoRollbackSkipped(OtaApp app, Release failed, String channel, ReleaseStat s, String why) {
        notify.send(app, "alert", "⚠ 版本 " + failed.shortId() + note(failed) + " 在 " + channel + " 有 " + s.getFailed() + " 台设备启动失败（失败率 "
                + s.getFailRate() + "%），" + why + "，没有自动回滚。请到后台处理（可以回滚到更早的版本或回退到安装包内置版本）");
    }

    /** Webhook 里的操作人：登录账号，或「令牌：名称」 */
    private static String operator() {
        String op = AuditService.operator();
        return op == null ? "系统" : op;
    }

    /** 群消息里的操作人：管理员的名字 */
    private String who() {
        String op = operator();
        return adminUsers.findByUsernameIgnoreCase(op).map(AdminUser::getName).filter(n -> !n.isBlank()).orElse(op);
    }

    private static String note(Release r) {
        return r.getMessage() == null || r.getMessage().isBlank() ? "" : "（" + r.getMessage() + "）";
    }

    private static Map<String, Object> base(OtaApp app, String by) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app", app.getSlug());
        if (by != null) m.put("by", by);
        return m;
    }

    private static Map<String, Object> release(Release r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("runtimeVersion", r.getRuntimeVersion());
        m.put("message", r.getMessage());
        m.put("forceUpdate", r.isForceUpdate());
        return m;
    }
}
