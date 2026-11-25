package com.ota.platform.ota;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 群机器人通知：按地址认出钉钉、飞书、企业微信，发一条文本消息。别的地址发 {"text": "..."}。
 * 钉钉、飞书的机器人如果开了关键词校验，关键词填「OTA」（每条消息都以【OTA · App 名】开头）。
 */
@Service
public class NotifyService {

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private final AppService apps;
    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ExecutorService pool = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "notify");
        t.setDaemon(true);
        return t;
    });

    private final com.ota.platform.webhook.NotifyDeliveries deliveries;
    private final com.ota.platform.settings.SettingService settings;

    public record Event(String id, String name, String desc) {}

    public static final java.util.List<Event> EVENTS = java.util.List.of(
            new Event("publish", "发布新版本", "谁把哪个版本发到了哪个渠道、灰度多少"),
            new Event("rollback", "回滚", "渠道回滚到之前的版本"),
            new Event("embedded", "回到内置版本", "让设备回到安装包里自带的版本"),
            new Event("switch", "切换 A/B 面", "审核模式和上线模式之间切换"),
            new Event("alert", "启动失败告警", "新版本在设备上启动失败、被退回，以及开了自动回滚时回滚的结果"));

    /** 每个 App 关掉了哪几种消息，逗号隔开；没设过就是全推 */
    private static final String MUTED = "notify.muted.";

    public NotifyService(AppService apps, ObjectMapper mapper, com.ota.platform.webhook.NotifyDeliveries deliveries,
                         com.ota.platform.settings.SettingService settings) {
        this.apps = apps;
        this.mapper = mapper;
        this.deliveries = deliveries;
        this.settings = settings;
    }

    public java.util.Set<String> muted(Long appId) {
        String v = settings.get(MUTED + appId, "");
        return v.isBlank() ? java.util.Set.of() : java.util.Set.of(v.split(","));
    }

    public void keepOnly(Long appId, java.util.Collection<String> wanted) {
        java.util.Set<String> on = wanted == null ? java.util.Set.of() : new java.util.HashSet<>(wanted);
        settings.set(MUTED + appId, String.join(",", EVENTS.stream().map(Event::id).filter(id -> !on.contains(id)).toList()));
    }

    public void send(OtaApp app, String event, String text) {
        if (!muted(app.getId()).contains(event)) send(app, text);
    }

    @PreDestroy
    void stop() {
        pool.shutdown();
    }

    /** 异步发送；没配置机器人时什么都不做 */
    private void send(OtaApp app, String text) {
        if (app.getNotifyUrl() == null || app.getNotifyUrl().isBlank()) return;
        Long appId = app.getId();
        String url = app.getNotifyUrl();
        String body = "【OTA · " + app.getName() + "】" + text;
        pool.execute(() -> {
            String result = post(url, body);
            apps.recordNotifyResult(appId, result);
            deliveries.record(appId, com.ota.platform.webhook.NotifyDelivery.BOT, text, url, result.startsWith("成功"), result, 1);
        });
    }

    /** 同步发一条测试消息，返回结果说明 */
    public String sendNow(OtaApp app, String text) {
        if (app.getNotifyUrl() == null || app.getNotifyUrl().isBlank()) return "没有配置机器人地址";
        String result = post(app.getNotifyUrl(), "【OTA · " + app.getName() + "】" + text);
        apps.recordNotifyResult(app.getId(), result);
        deliveries.record(app.getId(), com.ota.platform.webhook.NotifyDelivery.BOT, text, app.getNotifyUrl(), result.startsWith("成功"), result, 1);
        return result;
    }

    public static String kind(String url) {
        String host = "";
        try {
            host = URI.create(url.trim()).getHost();
        } catch (Exception ignored) {
        }
        host = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (host.endsWith("dingtalk.com")) return "钉钉";
        if (host.endsWith("feishu.cn") || host.endsWith("larksuite.com")) return "飞书";
        if (host.endsWith("qyapi.weixin.qq.com")) return "企业微信";
        return "通用";
    }

    @org.springframework.beans.factory.annotation.Value("${ota.demo.mode:false}")
    private boolean demo;

    private String post(String url, String text) {
        if (demo) return "成功：演示环境，没有真的发送";
        try {
            Object payload = switch (kind(url)) {
                case "飞书" -> Map.of("msg_type", "text", "content", Map.of("text", text));
                case "钉钉", "企业微信" -> Map.of("msgtype", "text", "text", Map.of("content", text));
                default -> Map.of("text", text);
            };
            HttpRequest req = HttpRequest.newBuilder(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            String b = res.body() == null ? "" : res.body().replaceAll("\\s+", " ").trim();
            boolean refused = b.matches(".*\"(errcode|code|StatusCode)\"\\s*:\\s*[1-9].*");
            String brief = b.length() > 120 ? b.substring(0, 120) + "…" : b;
            return (res.statusCode() / 100 == 2 && !refused ? "成功" : "失败") + "：HTTP " + res.statusCode() + (brief.isEmpty() ? "" : " " + brief);
        } catch (Exception e) {
            log.debug("机器人通知失败：{}", e.toString());
            return "失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
