package com.ota.platform.backup;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ota.platform.agent.Agent;
import com.ota.platform.agent.AgentRepo;
import com.ota.platform.app.AppService;
import com.ota.platform.app.Channel;
import com.ota.platform.app.OtaApp;
import com.ota.platform.codes.ActivationCode;
import com.ota.platform.codes.ActivationCodeRepo;
import com.ota.platform.common.ApiException;
import com.ota.platform.ota.TestDevice;
import com.ota.platform.ota.TestDeviceService;
import com.ota.platform.signing.PemSupport;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;

/**
 * App 备份：签名密钥对、口令接口密钥、Webhook、渠道，加上全部口令（口令所有 App 通用），用备份密码加密成一个文件。
 * 服务器丢了、数据库丢了，或者密钥被误换，都能用它把 App 恢复成原样，已发布的 App 和发出去的口令继续可用。
 * 加密：PBKDF2-HMAC-SHA256 从备份密码派生密钥，AES-256-GCM 加密，篡改或密码不对都解不开。
 */
@Service
public class BackupService {

    static final String FORMAT = "ota-app-backup";
    static final int VERSION = 1;
    private static final int ITERATIONS = 210_000;
    private static final int MIN_PASSWORD = 8;
    private static final byte[] AAD = (FORMAT + ":" + VERSION).getBytes(StandardCharsets.UTF_8);

    private final AppService apps;
    private final ActivationCodeRepo codeRepo;
    private final AgentRepo agentRepo;
    private final ObjectMapper mapper;
    private final TestDeviceService testDevices;
    private final SecureRandom random = new SecureRandom();

    private final com.ota.platform.app.AppIcons icons;

    public BackupService(AppService apps, ActivationCodeRepo codeRepo, AgentRepo agentRepo, ObjectMapper mapper,
                         TestDeviceService testDevices, com.ota.platform.app.AppIcons icons) {
        this.icons = icons;
        this.apps = apps;
        this.codeRepo = codeRepo;
        this.agentRepo = agentRepo;
        this.mapper = mapper;
        this.testDevices = testDevices;
    }

    public record Restored(OtaApp app, boolean created, int channelsAdded, int codesAdded, int codesKept) {}

    public byte[] export(OtaApp app, String password) {
        checkPassword(password);
        OtaApp a = apps.ensureCodeKey(app);
        ObjectNode payload = mapper.createObjectNode();
        ObjectNode info = payload.putObject("app");
        info.put("slug", a.getSlug());
        info.put("name", a.getName());
        info.put("keyId", a.getKeyId());
        info.put("privateKeyPem", PemSupport.toPem("PRIVATE KEY", apps.privateKey(a).getEncoded()));
        info.put("certificatePem", a.getCertificatePem());
        info.put("codeKey", a.getCodeKey());
        info.put("codeSecret", apps.codeSecret(a));
        info.put("grantKey", apps.grantKeyText(a));
        info.put("webhookUrl", a.getWebhookUrl());
        info.put("webhookSecret", apps.webhookSecret(a));
        info.put("patchEnabled", a.isPatchEnabled());
        info.put("redeemOpen", a.isRedeemOpen());
        info.put("notifyUrl", a.getNotifyUrl());
        info.put("downloadUrl", a.getDownloadUrl());
        icons.get(a.getId()).ifPresent(i -> info.putObject("icon").put("type", i.getContentType())
                .put("data", java.util.Base64.getEncoder().encodeToString(i.getData())));
        ArrayNode channels = payload.putArray("channels");
        for (Channel c : apps.channels(a)) channels.add(c.getName());
        ArrayNode testers = payload.putArray("testDevices");
        for (TestDevice t : testDevices.list(a)) {
            ObjectNode n = testers.addObject();
            n.put("clientId", t.getClientId());
            n.put("name", t.getName());
            n.put("channel", t.getChannel());
            if (t.isRolloutFirst()) n.put("rolloutFirst", true);
        }
        Map<Long, String> agents = new HashMap<>();
        for (Agent g : agentRepo.findAll()) agents.put(g.getId(), g.getUsername());
        ArrayNode codes = payload.putArray("codes");
        for (ActivationCode c : codeRepo.findAllByOrderByCreatedAtDesc()) {
            ObjectNode n = codes.addObject();
            n.put("code", c.getCode());
            n.put("remark", c.getRemark());
            n.put("maxUses", c.getMaxUses());
            n.put("usedCount", c.getUsedCount());
            n.put("enabled", Boolean.TRUE.equals(c.getEnabled()));
            n.put("expiresAt", c.getExpiresAt() == null ? null : c.getExpiresAt().toString());
            n.put("channel", c.getChannel());
            n.put("agent", c.getAgentId() == null ? null : agents.get(c.getAgentId()));
            if (c.getSubAgentId() != null && agents.containsKey(c.getSubAgentId())) n.put("subAgent", agents.get(c.getSubAgentId()));
        }
        try {
            byte[] salt = randomBytes(16);
            byte[] iv = randomBytes(12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(password, salt, ITERATIONS), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            byte[] data = cipher.doFinal(mapper.writeValueAsBytes(payload));
            ObjectNode out = mapper.createObjectNode();
            out.put("format", FORMAT);
            out.put("version", VERSION);
            out.put("app", a.getSlug());
            out.put("exportedAt", Instant.now().toString());
            ObjectNode kdf = out.putObject("kdf");
            kdf.put("alg", "PBKDF2-HMAC-SHA256");
            kdf.put("iterations", ITERATIONS);
            kdf.put("salt", Base64.getEncoder().encodeToString(salt));
            ObjectNode c = out.putObject("cipher");
            c.put("alg", "AES-256-GCM");
            c.put("iv", Base64.getEncoder().encodeToString(iv));
            out.put("data", Base64.getEncoder().encodeToString(data));
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(out);
        } catch (Exception e) {
            throw new IllegalStateException("生成备份失败：" + e.getMessage(), e);
        }
    }

    /** 备份属于哪个 App（信封里明文写着，不用密码） */
    public String slugOf(byte[] file) {
        try {
            return mapper.readTree(file).path("app").asText("");
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_BACKUP", "不是 App 备份文件");
        }
    }

    /**
     * 恢复：App 不存在就按备份新建；已存在时只有 overwrite 为 true 才用备份里的签名密钥和接口密钥覆盖。
     * 渠道和口令只补上缺的，已有的口令保持不变。
     */
    public Restored restore(byte[] file, String password, boolean overwrite) {
        JsonNode p = decrypt(file, password);
        JsonNode info = p.path("app");
        String slug = info.path("slug").asText();
        Optional<OtaApp> existing = apps.find(slug);
        if (existing.isPresent() && !overwrite) {
            throw ApiException.conflict("APP_EXISTS", "App " + slug + " 已存在。要用备份里的密钥把它改回去，勾选「覆盖已存在的 App」再恢复");
        }
        OtaApp app;
        boolean created = existing.isEmpty();
        if (created) {
            app = apps.create(slug, info.path("name").asText(slug), info.path("keyId").asText("main"),
                    info.path("privateKeyPem").asText(), info.path("certificatePem").asText());
        } else {
            app = apps.replaceSigningKey(existing.get(), info.path("keyId").asText("main"),
                    info.path("privateKeyPem").asText(), info.path("certificatePem").asText());
            apps.rename(app, info.path("name").asText(app.getName()));
        }
        app = apps.restoreSecrets(app, text(info, "codeKey"), text(info, "codeSecret"), text(info, "grantKey"),
                text(info, "webhookUrl"), text(info, "webhookSecret"));
        app = apps.setPatchEnabled(app, info.path("patchEnabled").asBoolean(true));
        if (app.isRedeemOpen() != info.path("redeemOpen").asBoolean(false)) app = apps.setRedeemOpen(app, info.path("redeemOpen").asBoolean(false));

        if (text(info, "notifyUrl") != null && app.getNotifyUrl() == null) app = apps.setNotify(app, text(info, "notifyUrl"));
        if (text(info, "downloadUrl") != null && app.getDownloadUrl() == null) app = apps.setDownloadUrl(app, text(info, "downloadUrl"));
        if (info.path("icon").path("data").isTextual() && app.getIconVersion() == null) {
            try {
                app = icons.set(app, java.util.Base64.getDecoder().decode(info.path("icon").path("data").asText()));
            } catch (RuntimeException e) {
                // 图标坏了不影响其他内容恢复
            }
        }
        int channelsAdded = 0;
        for (JsonNode c : p.path("channels")) {
            if (apps.findChannel(app, c.asText()).isEmpty()) {
                apps.createChannel(app, c.asText());
                channelsAdded++;
            }
        }
        for (JsonNode t : p.path("testDevices")) {
            String cid = text(t, "clientId");
            if (cid != null && testDevices.channelFor(app, cid).isEmpty() && apps.findChannel(app, t.path("channel").asText()).isPresent()) {
                testDevices.save(app, cid, text(t, "name"), t.path("channel").asText(), t.path("rolloutFirst").asBoolean(false));
            }
        }
        Map<String, Long> agents = new HashMap<>();
        Map<Long, Long> parentOf = new HashMap<>();
        for (Agent g : agentRepo.findAll()) {
            agents.put(g.getUsername(), g.getId());
            if (g.getParentId() != null) parentOf.put(g.getId(), g.getParentId());
        }
        int added = 0;
        int kept = 0;
        for (JsonNode n : p.path("codes")) {
            String code = n.path("code").asText();
            if (code.isBlank()) continue;
            if (codeRepo.existsByCode(code)) {
                kept++;
                continue;
            }
            ActivationCode c = new ActivationCode();
            c.setCode(code);
            c.setRemark(text(n, "remark"));
            c.setMaxUses(n.path("maxUses").asInt(0));
            c.setUsedCount(n.path("usedCount").asInt(0));
            c.setEnabled(n.path("enabled").asBoolean(true));
            String exp = text(n, "expiresAt");
            c.setExpiresAt(exp == null ? null : LocalDateTime.parse(exp));
            c.setChannel(text(n, "channel"));
            String agent = text(n, "agent");
            c.setAgentId(agent == null ? null : agents.get(agent));
            // 二级代理在，而且还是这个代理的下级，才恢复分给 TA
            Long sub = agents.get(text(n, "subAgent"));
            if (sub != null && c.getAgentId() != null && c.getAgentId().equals(parentOf.get(sub))) c.setSubAgentId(sub);
            codeRepo.save(c);
            added++;
        }
        return new Restored(app, created, channelsAdded, added, kept);
    }

    private JsonNode decrypt(byte[] file, String password) {
        JsonNode env;
        try {
            env = mapper.readTree(file);
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_BACKUP", "不是 App 备份文件");
        }
        if (!FORMAT.equals(env.path("format").asText())) throw ApiException.badRequest("BAD_BACKUP", "不是 App 备份文件");
        if (env.path("version").asInt() != VERSION) throw ApiException.badRequest("BAD_BACKUP", "备份文件版本不支持");
        if (password == null || password.isEmpty()) throw ApiException.badRequest("BAD_PASSWORD", "请输入备份密码");
        try {
            byte[] salt = Base64.getDecoder().decode(env.path("kdf").path("salt").asText());
            int iterations = env.path("kdf").path("iterations").asInt(ITERATIONS);
            byte[] iv = Base64.getDecoder().decode(env.path("cipher").path("iv").asText());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(password, salt, iterations), new GCMParameterSpec(128, iv));
            cipher.updateAAD(AAD);
            return mapper.readTree(cipher.doFinal(Base64.getDecoder().decode(env.path("data").asText())));
        } catch (AEADBadTagException e) {
            throw ApiException.badRequest("BAD_PASSWORD", "备份密码不对，或者备份文件被改动过");
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_BACKUP", "备份文件损坏：" + e.getMessage());
        }
    }

    private static SecretKeySpec key(String password, byte[] salt, int iterations) throws Exception {
        if (iterations < 100_000 || iterations > 5_000_000) throw new IllegalArgumentException("迭代次数不对");
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 256);
        byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new SecretKeySpec(k, "AES");
    }

    private static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("BAD_PASSWORD", "备份密码至少 " + MIN_PASSWORD + " 位");
        }
    }

    private byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        random.nextBytes(b);
        return b;
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() || v.asText().isEmpty() ? null : v.asText();
    }
}
