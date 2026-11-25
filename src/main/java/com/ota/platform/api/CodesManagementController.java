package com.ota.platform.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AppContext;
import com.ota.platform.auth.TokenPrincipal;
import com.ota.platform.codes.*;
import com.ota.platform.common.ApiException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.*;

/** 口令管理接口（管理令牌）：生成、修改、删除口令，查看激活记录，读取口令接口的 App Key 和密钥。口令所有 App 通用。 */
@RestController
@RequestMapping("/api/v1")
public class CodesManagementController {

    private final AppService apps;
    private final CodeAdminService codes;
    private final ActivationCodeRepo codeRepo;
    private final ActivationRecordRepo recordRepo;

    public CodesManagementController(AppService apps, CodeAdminService codes, ActivationCodeRepo codeRepo, ActivationRecordRepo recordRepo) {
        this.apps = apps;
        this.codes = codes;
        this.codeRepo = codeRepo;
        this.recordRepo = recordRepo;
    }

    @GetMapping("/codes")
    public List<Map<String, Object>> list(@AuthenticationPrincipal TokenPrincipal p, @RequestParam(required = false) Long agentId) {
        p.requireAdmin();
        return (agentId == null ? codeRepo.findAllByOrderByCreatedAtDesc() : codeRepo.findByAgentIdOrderByCreatedAtDesc(agentId)).stream()
                .map(CodesManagementController::view).toList();
    }

    /**
     * 新建口令。传 code 时建一个指定内容的口令；否则按 count（默认 1）、prefix、length（默认 6）随机生成。
     * 其余字段：maxUses（0 不限）、expiresAt（ISO 时间）、agentId、channel、remark。
     */
    @PostMapping("/codes")
    @ResponseStatus(HttpStatus.CREATED)
    public List<Map<String, Object>> create(@AuthenticationPrincipal TokenPrincipal p, @RequestBody JsonNode body) {
        p.requireAdmin();
        CodeAdminService.Spec spec = new CodeAdminService.Spec(text(body, "remark"), body.path("maxUses").asInt(0),
                body.hasNonNull("agentId") ? body.get("agentId").asLong() : null, text(body, "channel"), time(body, "expiresAt"));
        if (body.hasNonNull("code")) return List.of(view(codes.create(body.get("code").asText(), spec)));
        return codes.generate(body.path("count").asInt(1), text(body, "prefix"), body.path("length").asInt(6), spec)
                .stream().map(CodesManagementController::view).toList();
    }

    /** 修改：enabled、maxUses、agentId（0 或 null 表示不分配）、channel（空串表示不绑定）、remark、expiresAt（null 表示不过期） */
    @PatchMapping("/codes/{id}")
    public Map<String, Object> update(@AuthenticationPrincipal TokenPrincipal p, @PathVariable Long id, @RequestBody JsonNode body) {
        p.requireAdmin();
        ActivationCode c = codes.update(id,
                body.has("remark") ? body.path("remark").asText("") : null,
                body.has("maxUses") ? body.get("maxUses").asInt() : null,
                body.hasNonNull("agentId") ? body.get("agentId").asLong() : null, body.has("agentId"),
                text(body, "channel"), body.has("channel"),
                body.has("enabled") ? body.get("enabled").asBoolean() : null,
                time(body, "expiresAt"), body.has("expiresAt"));
        return view(c);
    }

    @DeleteMapping("/codes/{id}")
    public Map<String, Object> delete(@AuthenticationPrincipal TokenPrincipal p, @PathVariable Long id) {
        p.requireAdmin();
        ActivationCode c = codes.delete(id);
        return Map.of("deleted", c.getId());
    }

    /** 激活记录，status：all / confirmed / pending；app 填 App 标识只看这个 App 的 */
    @GetMapping("/redemptions")
    public List<Map<String, Object>> redemptions(@AuthenticationPrincipal TokenPrincipal p,
                                                 @RequestParam(name = "app", defaultValue = "") String appSlug,
                                                 @RequestParam(defaultValue = "all") String status,
                                                 @RequestParam(defaultValue = "") String code,
                                                 @RequestParam(defaultValue = "100") int limit) {
        p.requireAdmin();
        OtaApp only = appSlug.isBlank() ? null : app(appSlug.trim());
        Map<Long, String> slugs = new HashMap<>();
        for (OtaApp a : apps.all()) slugs.put(a.getId(), a.getSlug());
        var pr = PageRequest.of(0, Math.max(1, Math.min(limit, 1000)), Sort.by(Sort.Direction.DESC, "activatedAt"));
        return recordRepo.findAll((root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> where = new ArrayList<>();
            if (only != null) where.add(cb.equal(root.get("appId"), only.getId()));
            if ("confirmed".equals(status)) where.add(cb.isTrue(root.get("confirmed")));
            else if ("pending".equals(status)) {
                where.add(cb.or(cb.isNull(root.get("confirmed")), cb.isFalse(root.get("confirmed"))));
                where.add(cb.isNull(root.get("fastMs")));
                where.add(cb.isNull(root.get("dupOf")));
            } else if ("fast".equals(status)) where.add(cb.isNotNull(root.get("fastMs")));
            else if ("dup".equals(status)) where.add(cb.isNotNull(root.get("dupOf")));
            if (!code.isBlank()) where.add(cb.equal(root.get("codeText"), code.trim()));
            return cb.and(where.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }, pr).getContent().stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("app", slugs.get(r.getAppId()));
            m.put("code", r.getCodeText());
            m.put("codeId", r.getCodeId());
            m.put("deviceId", r.getDeviceId());
            m.put("ip", r.getDeviceIp());
            m.put("confirmed", Boolean.TRUE.equals(r.getConfirmed()));
            m.put("fastMs", r.getFastMs());
            m.put("dupOf", r.getDupOf());
            m.put("dupBy", r.getDupBy());
            m.put("activatedAt", r.getActivatedAt());
            m.put("confirmedAt", r.getConfirmedAt());
            return m;
        }).toList();
    }

    /** 口令接口的 App Key 和签名密钥（写进 App 里） */
    @GetMapping("/apps/{slug}/codes-key")
    public Map<String, Object> codesKey(@AuthenticationPrincipal TokenPrincipal p, @PathVariable String slug) {
        p.requireAdmin();
        OtaApp app = apps.ensureCodeKey(app(slug));
        return Map.of("appKey", app.getCodeKey(), "secret", apps.codeSecret(app));
    }

    private static String text(JsonNode body, String field) {
        JsonNode n = body.get(field);
        return n == null || n.isNull() ? null : n.asText();
    }

    private static LocalDateTime time(JsonNode body, String field) {
        String v = text(body, field);
        if (v == null || v.isBlank()) return null;
        try {
            return OffsetDateTime.parse(v).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(v);
            } catch (DateTimeParseException e2) {
                throw ApiException.badRequest("BAD_TIME", field + " 要是 ISO 时间，比如 2026-12-31T23:59:59+08:00");
            }
        }
    }

    static Map<String, Object> view(ActivationCode c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("code", c.getCode());
        m.put("enabled", Boolean.TRUE.equals(c.getEnabled()));
        m.put("maxUses", c.getMaxUses());
        m.put("usedCount", c.getUsedCount());
        m.put("agentId", c.getAgentId());
        m.put("channel", c.getChannel());
        m.put("remark", c.getRemark());
        m.put("expiresAt", c.getExpiresAt());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    /** 解析地址里的 App，并记到这次请求上（操作日志带上 App） */
    private OtaApp app(String slug) {
        OtaApp a = apps.require(slug);
        AppContext.set(a);
        return a;
    }
}
