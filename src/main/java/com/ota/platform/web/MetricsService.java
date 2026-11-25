package com.ota.platform.web;

import com.ota.platform.agent.AgentService;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.codes.ActivationRecordRepo;
import com.ota.platform.common.Hashes;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.ota.LaunchErrorService;
import com.ota.platform.ota.ReleaseStat;
import com.ota.platform.ota.ReleaseStatsService;
import com.ota.platform.settings.SettingService;
import com.ota.platform.stats.StatsService;
import com.ota.platform.web.admin.ReleaseViews;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.*;
import java.util.*;

/**
 * Prometheus 监控指标（文本格式），给 Prometheus / Grafana 采集、配告警。
 * 要带监控令牌（只能读指标），令牌只存哈希；没生成令牌时不开放。算一次要查每个 App，结果缓存 10 秒。
 */
@Service
public class MetricsService {

    private static final String TOKEN = "metrics.token", PREFIX = "metrics.token.prefix", CREATED = "metrics.token.at";
    private static final long CACHE_MS = 10_000;
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final SettingService settings;
    private final AppService apps;
    private final ReleaseViews views;
    private final ReleaseStatsService releaseStats;
    private final StatsService stats;
    private final ActivationRecordRepo records;
    private final LaunchErrorService launchErrors;
    private final AgentService agents;
    private final OtaProperties props;
    private final ObjectProvider<BuildProperties> build;
    private final SecureRandom random = new SecureRandom();
    private volatile String cached;
    private volatile long cachedAt;
    private volatile Instant lastScrape;

    public MetricsService(SettingService settings, AppService apps, ReleaseViews views, ReleaseStatsService releaseStats, StatsService stats,
                          ActivationRecordRepo records, LaunchErrorService launchErrors, AgentService agents, OtaProperties props,
                          ObjectProvider<BuildProperties> build) {
        this.settings = settings;
        this.apps = apps;
        this.views = views;
        this.releaseStats = releaseStats;
        this.stats = stats;
        this.records = records;
        this.launchErrors = launchErrors;
        this.agents = agents;
        this.props = props;
        this.build = build;
    }

    public boolean enabled() {
        return !settings.get(TOKEN, "").isBlank();
    }

    /** 令牌开头几位，页面上区分用 */
    public String prefix() {
        return settings.get(PREFIX, "");
    }

    public Instant createdAt() {
        String v = settings.get(CREATED, "");
        try {
            return v.isBlank() ? null : Instant.parse(v);
        } catch (DateTimeException e) {
            return null;
        }
    }

    public Instant lastScrape() {
        return lastScrape;
    }

    /** 生成新令牌（旧的马上失效），返回明文，只显示这一次 */
    public synchronized String rotate() {
        StringBuilder s = new StringBuilder("otm_");
        for (int i = 0; i < 32; i++) s.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        String token = s.toString();
        settings.set(TOKEN, Hashes.sha256Hex(token));
        settings.set(PREFIX, token.substring(0, 8));
        settings.set(CREATED, Instant.now().toString());
        return token;
    }

    public synchronized void disable() {
        settings.set(TOKEN, "");
        settings.set(PREFIX, "");
        settings.set(CREATED, "");
        cached = null;
    }

    public boolean matches(String token) {
        String hash = settings.get(TOKEN, "");
        return token != null && !token.isBlank() && !hash.isBlank() && Hashes.constantTimeEquals(hash, Hashes.sha256Hex(token.trim()));
    }

    public String render() {
        lastScrape = Instant.now();
        String c = cached;
        if (c != null && System.currentTimeMillis() - cachedAt < CACHE_MS) return c;
        String out = build();
        cached = out;
        cachedAt = System.currentTimeMillis();
        return out;
    }

    private String build() {
        Families f = new Families();
        f.add("ota_up", "gauge", "服务在运行", "", 1);
        BuildProperties b = build.getIfAvailable();
        f.add("ota_build_info", "gauge", "版本号", labels("version", b == null ? "dev" : b.getVersion()), 1);
        List<OtaApp> all = apps.all();
        f.add("ota_apps", "gauge", "App 数", "", all.size());
        f.add("ota_code_requests_pending", "gauge", "待审核的口令申请", "", agents.pendingCount());

        ZoneId zone;
        try {
            zone = ZoneId.of(props.getDisplayZone());
        } catch (DateTimeException e) {
            zone = ZoneId.systemDefault();
        }
        LocalDateTime todayStart = LocalDateTime.ofInstant(LocalDate.now(zone).atStartOfDay(zone).toInstant(), ZoneId.systemDefault());
        for (OtaApp app : all) {
            String a = labels("app", app.getSlug());
            f.add("ota_app_info", "gauge", "App 的名字，面板上显示用", labels("app", app.getSlug(), "name", app.getName()), 1);
            f.add("ota_app_side_b", "gauge", "运行模式：B 面（上线）为 1，A 面（审核）为 0", a, app.isRedeemOpen() ? 1 : 0);
            f.add("ota_devices_active_30d", "gauge", "近 30 天检查过更新的设备", a, stats.activeDevices(app.getId()));
            f.add("ota_activations_confirmed_total", "counter", "累计有效激活", a, records.countByAppIdAndConfirmedTrue(app.getId()));
            f.add("ota_activations_today", "gauge", "今天的有效激活（按显示时区算今天）", a,
                    records.countByAppIdAndConfirmedTrueAndConfirmedAtGreaterThanEqual(app.getId(), todayStart));
            f.add("ota_launch_errors_24h", "gauge", "近 24 小时上报的启动错误", a, launchErrors.countSince(app.getId(), Duration.ofHours(24)));

            List<ReleaseViews.Live> lives = views.live(app);
            Map<String, ReleaseStat> st = releaseStats.get(lives.stream().filter(l -> l.getRelease() != null).map(l -> l.getRelease().getId()).toList());
            for (ReleaseViews.Live l : lives) {
                String ch = labels("app", app.getSlug(), "channel", l.getChannel(), "runtime", l.getRuntime());
                f.add("ota_channel_embedded", "gauge", "渠道回退到了安装包内置版本时为 1", ch, l.isRollbackToEmbedded() ? 1 : 0);
                if (l.isRollbackToEmbedded() || l.getRelease() == null) continue;
                String rel = labels("app", app.getSlug(), "channel", l.getChannel(), "runtime", l.getRuntime(), "release", l.getRelease().shortId());
                ReleaseStat s = st.getOrDefault(l.getRelease().getId(), new ReleaseStat(l.getRelease().getId()));
                f.add("ota_release_rollout_percent", "gauge", "渠道线上版本的灰度比例", rel, l.getRollout());
                f.add("ota_release_offered_total", "counter", "线上版本推送给了多少台设备", rel, s.getOffered());
                f.add("ota_release_installed_total", "counter", "线上版本装上的设备", rel, s.getInstalled());
                f.add("ota_release_failed_total", "counter", "线上版本启动失败的设备", rel, s.getFailed());
                long base = Math.max(s.getOffered(), s.getInstalled() + s.getFailed());
                f.add("ota_release_failure_ratio", "gauge", "线上版本的启动失败率（0 到 1）", rel, base > 0 ? (double) s.getFailed() / base : 0);
            }
        }
        return f.text();
    }

    /** 指标按名字分组：每组先写 HELP 和 TYPE，再写各条数据 */
    private static final class Families {
        private final Map<String, StringBuilder> body = new LinkedHashMap<>();

        void add(String name, String type, String help, String labels, double value) {
            StringBuilder b = body.computeIfAbsent(name, k -> new StringBuilder("# HELP " + k + " " + help + "\n# TYPE " + k + " " + type + "\n"));
            b.append(name).append(labels).append(' ').append(number(value)).append('\n');
        }

        String text() {
            StringBuilder out = new StringBuilder();
            body.values().forEach(out::append);
            return out.toString();
        }

        private static String number(double v) {
            return v == Math.rint(v) && Math.abs(v) < 1e15 ? String.valueOf((long) v) : String.valueOf(v);
        }
    }

    private static String labels(String... kv) {
        StringBuilder s = new StringBuilder("{");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            if (i > 0) s.append(',');
            String v = kv[i + 1] == null ? "" : kv[i + 1];
            s.append(kv[i]).append("=\"").append(v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")).append('"');
        }
        return s.append('}').toString();
    }
}
