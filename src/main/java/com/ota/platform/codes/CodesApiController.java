package com.ota.platform.codes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AppContext;
import com.ota.platform.audit.AuditService;
import com.ota.platform.common.ApiException;
import com.ota.platform.common.Hashes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 口令接口（App 直接调用）。每个请求带：
 * X-App-Key、X-Timestamp（毫秒）、X-Nonce（每次随机）、X-Signature = hex(HMAC-SHA256(App 密钥, 时间戳 + "\n" + 随机数 + "\n" + 请求体))。
 * 时间戳和服务器相差不能超过 5 分钟，同一个随机数 5 分钟内只能用一次。
 */
@RestController
@RequestMapping("/v1/codes")
public class CodesApiController {

    static final long TS_TOLERANCE_MS = 5 * 60 * 1000;

    private final AppService apps;
    private final CodeService codes;
    private final GrantService grants;
    private final ActiveDays active;
    private final ObjectMapper mapper;
    private final RateLimiter redeemLimiter = new RateLimiter(20);
    private final Map<String, Long> nonces = new ConcurrentHashMap<>();

    public CodesApiController(AppService apps, CodeService codes, GrantService grants, ActiveDays active, ObjectMapper mapper) {
        this.apps = apps;
        this.codes = codes;
        this.grants = grants;
        this.active = active;
        this.mapper = mapper;
    }

    /** 兑换口令：成功返回授权 grant；口令绑定了渠道、这个 App 也有这个渠道时返回 channel，App 之后用它检查更新 */
    @PostMapping("/redeem")
    public Map<String, Object> redeem(@RequestBody(required = false) String body, HttpServletRequest req) {
        OtaApp app = authenticate(req, body);
        String ip = AuditService.clientIp(req);
        if (redeemLimiter.limited(app.getId() + ":" + ip)) throw ApiException.tooMany("请求太频繁，请稍后再试");
        JsonNode json = parse(body);
        String code = json.path("code").asText("");
        String deviceId = json.path("deviceId").asText("");
        if (code.isBlank()) throw ApiException.badRequest("BAD_REQUEST", "缺少 code");
        if (deviceId.isBlank()) throw ApiException.badRequest("BAD_REQUEST", "缺少 deviceId");
        CodeService.Redeemed r = codes.redeem(app, code, deviceId, ip);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", r.outcome().ok());
        if (r.outcome().ok()) {
            out.put("grant", grants.issue(app, r.code().getCode(), r.deviceId()));
            String ch = r.code().getChannel();
            out.put("channel", ch != null && apps.findChannel(app, ch).isPresent() ? ch : null);
            out.put("existing", r.outcome() != CodeService.Outcome.ISSUED);
        } else {
            out.put("error", r.outcome().error());
            out.put("message", r.outcome().message());
        }
        return out;
    }

    /** 校验授权是否有效 */
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody(required = false) String body, HttpServletRequest req) {
        OtaApp app = authenticate(req, body);
        JsonNode json = parse(body);
        Optional<GrantService.Grant> g = grants.parse(json.path("grant").asText(""));
        boolean valid = g.isPresent() && g.get().appId().equals(app.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("valid", valid);
        if (valid) {
            if (g.get().code() != null) out.put("code", g.get().code());
            CodeService.Fp fp = CodeService.deviceFp(json.path("init").asText(null), json.path("model").asText(null), AuditService.clientIp(req));
            active.seen(app.getId(), g.get(), fp == null ? null : fp.value());
        }
        return out;
    }

    /** App 用授权成功启动后调用，计入有效激活。重复调用没有副作用 */
    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody(required = false) String body, HttpServletRequest req) {
        OtaApp app = authenticate(req, body);
        JsonNode json = parse(body);
        Optional<GrantService.Grant> g = grants.parse(json.path("grant").asText(""));
        if (g.isEmpty() || !g.get().appId().equals(app.getId())) return Map.of("ok", false, "error", "INVALID_GRANT");
        CodeService.Fp fp = CodeService.deviceFp(json.path("init").asText(null), json.path("model").asText(null), AuditService.clientIp(req));
        boolean first = codes.confirmGrant(app, g.get(), fp);
        active.seen(app.getId(), g.get(), fp == null ? null : fp.value());
        return Map.of("ok", true, "confirmed", first);
    }

    private OtaApp authenticate(HttpServletRequest req, String body) {
        String key = req.getHeader("X-App-Key");
        String ts = req.getHeader("X-Timestamp");
        String nonce = req.getHeader("X-Nonce");
        String sig = req.getHeader("X-Signature");
        if (key == null || ts == null || nonce == null || sig == null) {
            throw ApiException.unauthorized("MISSING_AUTH", "缺少 X-App-Key、X-Timestamp、X-Nonce 或 X-Signature");
        }
        OtaApp app = apps.findByCodeKey(key).orElseThrow(() -> ApiException.unauthorized("BAD_APP_KEY", "App Key 无效"));
        AppContext.set(app);
        long t;
        try {
            t = Long.parseLong(ts.trim());
        } catch (NumberFormatException e) {
            throw ApiException.unauthorized("BAD_TIMESTAMP", "X-Timestamp 要是毫秒时间戳");
        }
        long now = System.currentTimeMillis();
        if (Math.abs(now - t) > TS_TOLERANCE_MS) throw ApiException.unauthorized("STALE_TIMESTAMP", "时间戳和服务器相差超过 5 分钟");
        if (nonce.length() < 8 || nonce.length() > 64) throw ApiException.unauthorized("BAD_NONCE", "X-Nonce 要 8–64 位");
        String secret = apps.codeSecret(app);
        String expected = Hashes.hmacSha256Hex(secret.getBytes(StandardCharsets.UTF_8), ts.trim() + "\n" + nonce + "\n" + (body == null ? "" : body));
        if (!Hashes.constantTimeEquals(expected, sig.trim().toLowerCase())) throw ApiException.unauthorized("BAD_SIGNATURE", "签名不正确");
        if (nonces.size() > 200_000) nonces.entrySet().removeIf(e -> e.getValue() < now);
        Long prev = nonces.putIfAbsent(app.getId() + ":" + nonce, now + TS_TOLERANCE_MS * 2);
        if (prev != null && prev > now) throw ApiException.unauthorized("REPLAYED", "这个随机数已经用过");
        if (prev != null) nonces.put(app.getId() + ":" + nonce, now + TS_TOLERANCE_MS * 2);
        return app;
    }

    private JsonNode parse(String body) {
        if (body == null || body.isBlank()) return mapper.createObjectNode();
        try {
            return mapper.readTree(body);
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_JSON", "请求体不是有效的 JSON");
        }
    }
}
