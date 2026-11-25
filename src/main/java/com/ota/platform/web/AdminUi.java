package com.ota.platform.web;

import com.ota.platform.audit.AuditService;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.config.OtaProperties;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 后台模板用的展示工具，模板里通过 ${@ui.xxx(...)} 调用。 */
@Component("ui")
public class AdminUi {

    private static final Map<String, String> ACTION_LABELS = Map.ofEntries(
            Map.entry("SWITCH", "切换模式"),
            Map.entry("UPLOAD", "上传更新包"),
            Map.entry("ADD_URL", "添加外部链接"),
            Map.entry("ACTIVATE", "发布版本"),
            Map.entry("ROLLBACK", "回滚"),
            Map.entry("RELEASE_GUARD", "自动回滚设置"),
            Map.entry("ACTIVATION_PACKAGE", "口令更新包"),
            Map.entry("METRICS_TOKEN", "监控令牌"),
            Map.entry("DELETE", "删除版本"),
            Map.entry("CODE_CREATE", "新建口令"),
            Map.entry("CODE_TOGGLE", "启停口令"),
            Map.entry("CODE_DELETE", "删除口令"),
            Map.entry("CODE_SUBMIT", "提交口令"),
            Map.entry("CONFIRMED", "激活确认"),
            Map.entry("CONFIRM_FAST", "确认太快"),
            Map.entry("CODE_DUP", "重复激活"),
            Map.entry("CODE_UPDATE", "修改口令"),
            Map.entry("AGENT_CREATE", "新建代理"),
            Map.entry("AGENT_UPDATE", "修改代理"),
            Map.entry("AGENT_TOGGLE", "启停代理"),
            Map.entry("AGENT_DELETE", "删除代理"),
            Map.entry("AGENT_REFRESH", "更新代理数据"),
            Map.entry("AGENT_LOGIN", "代理登录"),
            Map.entry("SUB_AGENT_CREATE", "新建二级代理"),
            Map.entry("SUB_AGENT_UPDATE", "修改二级代理"),
            Map.entry("SUB_AGENT_TOGGLE", "启停二级代理"),
            Map.entry("SUB_AGENT_DELETE", "删除二级代理"),
            Map.entry("SUB_AGENT_CODES", "分配二级代理口令"),
            Map.entry("SUB_AGENT_REFRESH", "更新二级代理数据"),
            Map.entry("SCREEN_SHARE_CREATE", "分享数据大屏"),
            Map.entry("SCREEN_SHARE_REVOKE", "取消大屏分享"),
            Map.entry("SUB_NOTICE_CREATE", "给二级代理发公告"),
            Map.entry("SUB_NOTICE_UPDATE", "修改二级代理公告"),
            Map.entry("SUB_NOTICE_DELETE", "删除二级代理公告"),
            Map.entry("SIGNING_UPDATE", "更新签名"),
            Map.entry("SETTINGS_APPEARANCE", "修改外观"),
            Map.entry("SETTINGS_ACCOUNT", "修改管理员"),
            Map.entry("LOG_CLEAR", "清空日志"),
            Map.entry("VERIFY", "校验版本"),
            Map.entry("APP_CREATE", "新建 App"),
            Map.entry("APP_UPDATE", "修改 App"),
            Map.entry("CHANNEL_CREATE", "新建渠道"),
            Map.entry("TOKEN_CREATE", "新建令牌"),
            Map.entry("TOKEN_REVOKE", "撤销令牌"),
            Map.entry("WEBHOOK_UPDATE", "修改 Webhook"),
            Map.entry("APP_BACKUP", "下载备份"),
            Map.entry("APP_RESTORE", "恢复备份"),
            Map.entry("APP_DELETE", "删除 App"),
            Map.entry("CHANNEL_DELETE", "删除渠道"),
            Map.entry("RELEASE_UPDATE", "修改版本"),
            Map.entry("STORAGE_CLEAN", "清理存储"),
            Map.entry("CODE_EXPORT", "导出口令"),
            Map.entry("RECORD_EXPORT", "导出激活记录"),
            Map.entry("ADMIN_LOGIN", "管理员登录"),
            Map.entry("ADMIN_CREATE", "新建管理员"),
            Map.entry("ADMIN_UPDATE", "修改管理员"),
            Map.entry("ADMIN_DELETE", "删除管理员"),
            Map.entry("ACCOUNT_UPDATE", "修改账号"),
            Map.entry("TESTER_SAVE", "测试设备"),
            Map.entry("TESTER_DELETE", "移除测试设备"),
            Map.entry("NOTIFY_UPDATE", "修改群机器人"),
            Map.entry("LOGIN_LOCKED", "登录锁定"),
            Map.entry("LOGIN_2FA_LOCKED", "两步验证锁定"),
            Map.entry("LOGIN_RECOVERY", "恢复码登录"),
            Map.entry("TOTP_ENABLE", "开启两步验证"),
            Map.entry("TOTP_DISABLE", "关闭两步验证"),
            Map.entry("TOTP_RECOVERY", "重新生成恢复码"),
            Map.entry("TOTP_RESET", "重置两步验证"),
            Map.entry("SESSION_REVOKE", "下线登录设备"),
            Map.entry("SECURITY_SETTINGS", "修改登录安全"),
            Map.entry("LOG_EXPORT", "导出日志"),
            Map.entry("INSTALL_REOPEN", "重开安装引导"),
            Map.entry("DOMAINS_UPDATE", "修改访问域名"),
            Map.entry("STORAGE_SWITCH", "切换文件存储"),
            Map.entry("INSTALLER_UPLOAD", "上传安装包"),
            Map.entry("INSTALLER_DELETE", "删除安装包"),
            Map.entry("CODE_REQUEST", "代理申请口令"),
            Map.entry("CODE_REQUEST_APPROVE", "同意口令申请"),
            Map.entry("CODE_REQUEST_REJECT", "拒绝口令申请"),
            Map.entry("CODE_REQUEST_CANCEL", "撤回口令申请"),
            Map.entry("AGENT_PASSWORD", "代理改密码"),
            Map.entry("AGENT_EXPORT", "代理导出记录"),
            Map.entry("DEVICE_EXPORT", "导出设备"),
            Map.entry("AGENTS_EXPORT", "导出代理列表"),
            Map.entry("AGENT_RECORDS_EXPORT", "导出代理激活记录"),
            Map.entry("SCHEDULE", "计划任务"), Map.entry("NOTICE_CREATE", "发布公告"), Map.entry("NOTICE_UPDATE", "修改公告"),
            Map.entry("NOTICE_DELETE", "删除公告"), Map.entry("PROJECT_BACKUP", "项目备份"), Map.entry("PROJECT_RESTORE", "项目恢复"),
            Map.entry("DEMO_RESET", "恢复演示数据"), Map.entry("SETTINGS_DEMO", "演示账号设置"));

    private static final Map<String, String> SOURCE_LABELS = Map.of(
            "upload", "上传", "cli", "命令行", "external", "外部链接", "republish", "重新发布");

    private static final DateTimeFormatter MD_HM = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter MD_HMS = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
    private static final DateTimeFormatter YMD_HM = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    /** 数据库里的时间是服务器 JVM 时区，展示时统一换成这个时区。 */
    private final ZoneId zone;

    public AdminUi(OtaProperties props) {
        this.zone = ZoneId.of(props.getDisplayZone());
    }

    // ===== 时间（LocalDateTime 按服务器时区理解，Instant 直接换算）=====

    /** 后台显示时区的今天 0 点，换成服务器时区的时间（数据库里的 LocalDateTime 按服务器时区存） */
    public java.time.LocalDateTime todayStart() {
        return java.time.LocalDate.now(zone).atStartOfDay(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** 30 → 30 分钟，480 → 8 小时，10080 → 7 天 */
    /** 迷你趋势线的 SVG 路径（宽 w、高 h，上下各留 3px）；area 为 true 时闭合成填充区域 */
    public static String sparkPath(long[] values, int w, int h, boolean area) {
        if (values.length < 2) return "";
        long max = 0;
        for (long v : values) max = Math.max(max, v);
        StringBuilder d = new StringBuilder();
        double step = (double) w / (values.length - 1);
        for (int i = 0; i < values.length; i++) {
            double y = max == 0 ? h - 3 : (h - 3) - values[i] * (h - 6.0) / max;
            d.append(i == 0 ? 'M' : 'L').append(String.format(java.util.Locale.ROOT, "%.1f,%.1f", i * step, y));
        }
        if (area) d.append('L').append(w).append(',').append(h).append("L0,").append(h).append('Z');
        return d.toString();
    }

    public static String minutes(int m) {
        if (m % 1440 == 0) return (m / 1440) + " 天";
        if (m % 60 == 0) return (m / 60) + " 小时";
        return m + " 分钟";
    }

    public String dt(Object t) {
        ZonedDateTime z = local(t);
        if (z == null) return "—";
        return (z.getYear() == ZonedDateTime.now(zone).getYear() ? MD_HM : YMD_HM).format(z);
    }

    public String dts(Object t) {
        ZonedDateTime z = local(t);
        if (z == null) return "—";
        return z.getYear() == ZonedDateTime.now(zone).getYear() ? MD_HMS.format(z) : FULL.format(z);
    }

    public String full(Object t) {
        ZonedDateTime z = local(t);
        return z == null ? "" : FULL.format(z);
    }

    public String ago(Object t) {
        ZonedDateTime z = local(t);
        if (z == null) return "—";
        ZonedDateTime now = ZonedDateTime.now(zone);
        long sec = Duration.between(z, now).getSeconds();
        if (sec < 60) return "刚刚";
        if (sec < 3600) return (sec / 60) + " 分钟前";
        LocalDate day = z.toLocalDate();
        LocalDate today = now.toLocalDate();
        if (day.equals(today)) return "今天 " + HM.format(z);
        if (day.equals(today.minusDays(1))) return "昨天 " + HM.format(z);
        return dt(t);
    }

    /** 展示时区的今天 0 点，换算回服务器时区，用于查询。 */
    public LocalDateTime startOfToday() {
        return LocalDate.now(zone).atStartOfDay(zone)
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    public LocalDate today() {
        return LocalDate.now(zone);
    }

    /** 服务器时区的时间点落在展示时区的哪一天 */
    public LocalDate localDate(LocalDateTime t) {
        return local(t).toLocalDate();
    }

    private ZonedDateTime local(Object t) {
        if (t instanceof LocalDateTime l) return l.atZone(ZoneId.systemDefault()).withZoneSameInstant(zone);
        if (t instanceof Instant i) return i.atZone(zone);
        return null;
    }

    // ===== 业务展示 =====

    public String action(String code) {
        return code == null ? "—" : ACTION_LABELS.getOrDefault(code, code);
    }

    /** 日志类型的颜色：删除、回滚、撤销红；切换模式、锁定、换签名黄；发布、激活绿；其他改动蓝；登录这类中性 */
    public String tone(String code) {
        if (code == null) return "";
        if (code.equals("DELETE") || code.endsWith("_DELETE") || code.equals("ROLLBACK") || code.equals("TOKEN_REVOKE")
                || code.equals("LOG_CLEAR") || code.equals("STORAGE_CLEAN")) return "tone-danger";
        if (code.equals("SWITCH") || code.equals("LOGIN_LOCKED") || code.equals("LOGIN_2FA_LOCKED") || code.equals("SIGNING_UPDATE")
                || code.equals("APP_RESTORE") || code.equals("TOTP_DISABLE") || code.equals("TOTP_RESET") || code.equals("STORAGE_SWITCH")
                || code.equals("CODE_REQUEST_REJECT") || code.equals("CONFIRM_FAST") || code.equals("CODE_DUP")) return "tone-warn";
        if (code.equals("LOGIN_RECOVERY")) return "";
        if (code.equals("ACTIVATE") || code.equals("UPLOAD") || code.equals("ADD_URL") || code.equals("CONFIRMED")
                || code.equals("CODE_REQUEST_APPROVE")) return "tone-ok";
        if (code.endsWith("_LOGIN") || code.equals("CODE_SUBMIT") || code.equals("VERIFY")) return "";
        return "tone-info";
    }

    public boolean deviceEvent(String code) {
        return AuditService.DEVICE_EVENTS.contains(code);
    }

    public String source(String source) {
        return source == null ? "—" : SOURCE_LABELS.getOrDefault(source, source);
    }

    /** 可用 / 停用 / 已过期 / 已用完（次数按代理看到的算） */
    public String codeStatus(ActivationCode c) {
        if (!Boolean.TRUE.equals(c.getEnabled())) return "停用";
        if (c.getExpiresAt() != null && LocalDateTime.now().isAfter(c.getExpiresAt())) return "已过期";
        int max = c.getMaxUses() == null ? 0 : c.getMaxUses();
        if (max > 0 && c.limitUsed() >= max) return "已用完";
        return "可用";
    }

    public int percent(ActivationCode c) {
        int max = c.getMaxUses() == null ? 0 : c.getMaxUses();
        if (max <= 0) return 0;
        return Math.min(100, c.limitUsed() * 100 / max);
    }

    /** 地址里的域名，非默认端口保留（和地址栏里看到的一样） */
    public String host(String url) {
        try {
            java.net.URI u = java.net.URI.create(url.trim());
            if (u.getHost() == null) return url;
            int port = u.getPort();
            boolean dflt = port < 0 || ("http".equalsIgnoreCase(u.getScheme()) && port == 80) || ("https".equalsIgnoreCase(u.getScheme()) && port == 443);
            return dflt ? u.getHost() : u.getHost() + ":" + port;
        } catch (Exception e) {
            return url;
        }
    }

    public String size(Number bytes) {
        if (bytes == null) return "—";
        long b = bytes.longValue();
        if (b < 1024) return b + " B";
        if (b < 1048576) return String.format("%.1f KB", b / 1024.0);
        return String.format("%.1f MB", b / 1048576.0);
    }

    public String dataMode(String mode) {
        if ("DAILY".equals(mode)) return "每日 0 点";
        if ("MANUAL".equals(mode)) return "手动更新";
        return "实时";
    }

    /** 给代理看的 IP 只保留网段 */
    public String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return "—";
        int dot = ip.lastIndexOf('.');
        if (dot > 0 && ip.indexOf(':') < 0) return ip.substring(0, dot) + ".*";
        String[] parts = ip.split(":");
        return parts.length > 2 ? parts[0] + ":" + parts[1] + ":*" : "*";
    }

    public String shortId(String id) {
        if (id == null) return "—";
        return id.length() > 14 ? id.substring(0, 8) + "…" + id.substring(id.length() - 4) : id;
    }

    /** 给代理看的设备号：两头各留几位，中间换成 ****，再短的也不会原样露出来 */
    public String maskDevice(String id) {
        if (id == null || id.isEmpty()) return "—";
        if (id.length() <= 4) return id.charAt(0) + "***";
        int keep = Math.max(1, Math.min(4, id.length() / 4));
        return id.substring(0, keep) + "****" + id.substring(id.length() - keep);
    }

    /** 版本 ID 只显示前 8 位 */
    public String rid(String id) {
        return id == null ? "—" : id.length() > 8 ? id.substring(0, 8) : id;
    }

    /** 拼筛选链接，空值、"all"、第 1 页都省略，地址栏保持干净。 */
    public String url(String path, Object... kv) {
        String q = query(kv);
        return q.isEmpty() ? path : path + "?" + q;
    }

    /** 和 url 一样，只返回 ? 后面的部分 */
    public String query(Object... kv) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v == null) continue;
            String s = v.toString().trim();
            if (s.isEmpty() || s.equals("all") || ("page".equals(kv[i]) && s.equals("1"))) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(kv[i]).append('=').append(URLEncoder.encode(s, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    public String zoneId() {
        return zone.getId();
    }

    /** 分页链接：base 是带好筛选参数的地址，第 1 页不带 page 参数 */
    public String page(String base, int page) {
        if (page <= 1) return base;
        return base + (base.contains("?") ? "&" : "?") + "page=" + page;
    }

    /** 要显示的页码，0 表示省略号：1 … 4 5 6 … 20 */
    public List<Integer> pages(int current, int total) {
        List<Integer> out = new ArrayList<>();
        int from = Math.max(2, current - 2);
        int to = Math.min(total - 1, current + 2);
        out.add(1);
        if (from > 2) out.add(0);
        for (int i = from; i <= to; i++) out.add(i);
        if (to < total - 1) out.add(0);
        if (total > 1) out.add(total);
        return out;
    }

    /** 展示时区的日期 yyyy-MM-dd，给 date 输入框用 */
    public String date(Object t) {
        ZonedDateTime z = local(t);
        return z == null ? "" : DateTimeFormatter.ISO_LOCAL_DATE.format(z);
    }

    /** 展示时区某天 0 点，换成服务器时区的时间，查询用 */
    public LocalDateTime startOf(LocalDate day) {
        return day.atStartOfDay(zone).withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
    }

    /** 1.2 GB / 34.5 MB / 12.0 KB */
    public String bytes(Number bytes) {
        if (bytes == null) return "—";
        long b = bytes.longValue();
        if (b >= 1L << 30) return String.format("%.2f GB", b / (double) (1L << 30));
        return size(b);
    }
}
