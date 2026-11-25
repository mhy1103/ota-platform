package com.ota.platform.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.common.Hashes;
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
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 口令事件通知。POST JSON 到 App 配置的地址，带 X-OTA-Signature: sha256=HMAC(密钥, 时间戳 + "." + 请求体)。
 * 对方返回 2xx 算成功，否则 5 秒、30 秒后各重试一次。
 */
@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final long[] RETRY_DELAYS_MS = {5_000, 30_000};

    private final AppService apps;
    private final ObjectMapper mapper;
    private final NotifyDeliveries deliveries;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final ScheduledExecutorService pool = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "webhook");
        t.setDaemon(true);
        return t;
    });

    public WebhookService(AppService apps, ObjectMapper mapper, NotifyDeliveries deliveries) {
        this.apps = apps;
        this.mapper = mapper;
        this.deliveries = deliveries;
    }

    @PreDestroy
    void stop() {
        pool.shutdownNow();
    }

    public record Delivery(String url, String body, String secret, Long appId, String event) {}

    /** 异步发送；App 没配置 Webhook 时什么都不做 */
    public void emit(OtaApp app, String event, Map<String, Object> data) {
        Delivery d = prepare(app, event, data);
        if (d != null) pool.execute(() -> attempt(d, 0));
    }

    /** 同步发送一次（后台的「发送测试」），返回结果说明 */
    public String sendNow(OtaApp app, String event, Map<String, Object> data) {
        Delivery d = prepare(app, event, data);
        if (d == null) return "没有配置 Webhook 地址";
        String result = post(d);
        apps.recordWebhookResult(d.appId(), result + " · " + event);
        deliveries.record(d.appId(), NotifyDelivery.WEBHOOK, event, d.url(), result.startsWith("HTTP 2"), result, 1);
        return result;
    }

    private Delivery prepare(OtaApp app, String event, Map<String, Object> data) {
        if (app.getWebhookUrl() == null || app.getWebhookUrl().isBlank()) return null;
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("id", UUID.randomUUID().toString());
        envelope.put("event", event);
        envelope.put("createdAt", Instant.now().toString());
        envelope.put("data", data);
        try {
            return new Delivery(app.getWebhookUrl(), mapper.writeValueAsString(envelope), apps.webhookSecret(app), app.getId(), event);
        } catch (Exception e) {
            log.warn("Webhook 序列化失败：{}", e.toString());
            return null;
        }
    }

    private void attempt(Delivery d, int retry) {
        String result = post(d);
        boolean ok = result.startsWith("HTTP 2");
        if (ok || retry >= RETRY_DELAYS_MS.length) {
            apps.recordWebhookResult(d.appId(), result + " · " + d.event() + (retry > 0 ? " · 第 " + (retry + 1) + " 次" : ""));
            deliveries.record(d.appId(), NotifyDelivery.WEBHOOK, d.event(), d.url(), ok, result, retry + 1);
            if (!ok) log.warn("Webhook 发送失败（已重试 {} 次）{}：{}", retry, d.url(), result);
            return;
        }
        pool.schedule(() -> attempt(d, retry + 1), RETRY_DELAYS_MS[retry], TimeUnit.MILLISECONDS);
    }

    @org.springframework.beans.factory.annotation.Value("${ota.demo.mode:false}")
    private boolean demo;

    private String post(Delivery d) {
        if (demo) return "HTTP 200（演示环境，没有真的发送）";
        String ts = String.valueOf(System.currentTimeMillis());
        String sig = d.secret() == null ? "" : Hashes.hmacSha256Hex(d.secret().getBytes(StandardCharsets.UTF_8), ts + "." + d.body());
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(d.url()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "OTA-Platform-Webhook/1.0")
                    .header("X-OTA-Event", d.event())
                    .header("X-OTA-Timestamp", ts)
                    .header("X-OTA-Signature", "sha256=" + sig)
                    .POST(HttpRequest.BodyPublishers.ofString(d.body(), StandardCharsets.UTF_8))
                    .build();
            HttpResponse<Void> res = http.send(req, HttpResponse.BodyHandlers.discarding());
            return "HTTP " + res.statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "已中断";
        } catch (Exception e) {
            return "连接失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }
}
