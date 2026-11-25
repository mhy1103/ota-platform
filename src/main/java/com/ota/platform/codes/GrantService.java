package com.ota.platform.codes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.common.Hashes;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Optional;

/**
 * 口令兑换成功后签发的授权凭证。
 * 格式：g1.{base64url(JSON：App 标识、口令、设备 ID、签发时间)}.{HMAC}，密钥是每个 App 自己的授权密钥，
 * 随 App 备份走，换服务器恢复后已签发的授权依然有效。
 */
@Service
public class GrantService {

    private static final String PREFIX = "g1.";

    /** 授权里带的是口令内容（code），不是口令 ID：恢复备份后口令 ID 会变 */
    public record Grant(Long appId, String code, String deviceId, long issuedAt) {}

    private final AppService apps;
    private final ObjectMapper mapper;

    public GrantService(AppService apps, ObjectMapper mapper) {
        this.apps = apps;
        this.mapper = mapper;
    }

    public String issue(OtaApp app, String code, String deviceId) {
        ObjectNode p = mapper.createObjectNode();
        p.put("a", app.getSlug());
        p.put("c", code);
        p.put("d", deviceId);
        p.put("t", System.currentTimeMillis());
        try {
            String p64 = Base64.getUrlEncoder().withoutPadding().encodeToString(mapper.writeValueAsBytes(p));
            return PREFIX + p64 + "." + mac(apps.grantKey(app), p64);
        } catch (Exception e) {
            throw new IllegalStateException("签发授权失败", e);
        }
    }

    /** 校验并解析凭证；无效返回空 */
    public Optional<Grant> parse(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String t = token.trim();
        if (!t.startsWith(PREFIX)) return Optional.empty();
        String[] parts = t.split("\\.");
        if (parts.length != 3) return Optional.empty();
        try {
            JsonNode p = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            Optional<OtaApp> app = apps.find(p.path("a").asText());
            if (app.isEmpty() || !Hashes.constantTimeEquals(mac(apps.grantKey(app.get()), parts[1]), parts[2])) return Optional.empty();
            return Optional.of(new Grant(app.get().getId(), p.path("c").asText(), p.path("d").asText(), p.path("t").asLong()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String mac(byte[] key, String p64) {
        return Hashes.hmacSha256Hex(key, PREFIX + p64).substring(0, 32);
    }
}
