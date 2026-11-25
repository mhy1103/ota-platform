package com.ota.platform.app;

import com.ota.platform.common.ApiException;
import com.ota.platform.release.DeploymentRepository;
import com.ota.platform.signing.CodeSigning;
import com.ota.platform.signing.KeyVault;
import com.ota.platform.signing.PemSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class AppService {

    public static final String DEFAULT_CHANNEL = "production";

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9-]{1,38}[a-z0-9]");
    private static final Pattern CHANNEL = Pattern.compile("[a-z0-9][a-z0-9._-]{0,39}");
    private static final Pattern KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,40}");

    private final OtaAppRepository apps;
    private final ChannelRepository channels;
    private final DeploymentRepository deployments;
    private final KeyVault vault;
    private final Map<Long, PrivateKey> keyCache = new ConcurrentHashMap<>();
    private final Map<Long, String> codeSecretCache = new ConcurrentHashMap<>();
    private final Map<Long, byte[]> grantKeyCache = new ConcurrentHashMap<>();

    public AppService(OtaAppRepository apps, ChannelRepository channels, DeploymentRepository deployments, KeyVault vault) {
        this.apps = apps;
        this.channels = channels;
        this.deployments = deployments;
        this.vault = vault;
    }

    /** 创建 App。不传私钥和证书时自动生成；传了就校验后导入（比如沿用已发布 App 里内置的证书） */
    @Transactional
    public OtaApp create(String slug, String name, String keyId, String privateKeyPem, String certificatePem) {
        String s = slug == null ? "" : slug.trim();
        if (!SLUG.matcher(s).matches()) {
            throw ApiException.badRequest("BAD_SLUG", "slug 只能用小写字母、数字和 -，3–40 位，不能以 - 开头或结尾");
        }
        if (apps.existsBySlug(s)) throw ApiException.conflict("APP_EXISTS", "App " + s + " 已存在");
        String kid = keyId == null || keyId.isBlank() ? "main" : keyId.trim();
        if (!KEY_ID.matcher(kid).matches()) throw ApiException.badRequest("BAD_KEY_ID", "keyId 只能用字母、数字和 . _ -");
        String displayName = name == null || name.isBlank() ? s : name.trim();
        if (displayName.length() > 100) throw ApiException.badRequest("BAD_NAME", "名称最多 100 字");

        CodeSigning.Material material = material(privateKeyPem, certificatePem, displayName);
        OtaApp app = new OtaApp();
        app.setSlug(s);
        app.setName(displayName);
        app.setKeyId(kid);
        app.setPrivateKeyEnc(vault.encrypt(material.privateKeyPem()));
        app.setCertificatePem(material.certificatePem());
        app.setKeyUpdatedAt(Instant.now());
        app.setCodeKey("app_" + vault.randomToken(12));
        app.setCodeSecretEnc(vault.encrypt(vault.randomToken(24)));
        app.setGrantKeyEnc(vault.encrypt(vault.randomToken(32)));
        apps.save(app);
        channels.save(new Channel(app.getId(), DEFAULT_CHANNEL));
        return app;
    }

    /** 更换签名密钥。证书编进了 App，换了以后已经装好的 App 收不到更新，只在整套换密钥时用 */
    @Transactional
    public OtaApp replaceSigningKey(OtaApp app, String keyId, String privateKeyPem, String certificatePem) {
        CodeSigning.Material material = material(privateKeyPem, certificatePem, app.getName());
        if (keyId != null && !keyId.isBlank()) {
            if (!KEY_ID.matcher(keyId.trim()).matches()) throw ApiException.badRequest("BAD_KEY_ID", "keyId 只能用字母、数字和 . _ -");
            app.setKeyId(keyId.trim());
        }
        app.setPrivateKeyEnc(vault.encrypt(material.privateKeyPem()));
        app.setCertificatePem(material.certificatePem());
        app.setKeyUpdatedAt(Instant.now());
        keyCache.remove(app.getId());
        return apps.save(app);
    }

    private static CodeSigning.Material material(String privateKeyPem, String certificatePem, String commonName) {
        boolean hasKey = privateKeyPem != null && !privateKeyPem.isBlank();
        boolean hasCert = certificatePem != null && !certificatePem.isBlank();
        if (hasKey != hasCert) throw ApiException.badRequest("BAD_SIGNING_KEY", "私钥和证书要一起提供，或者都不提供（自动生成）");
        try {
            return hasKey ? CodeSigning.validate(privateKeyPem, certificatePem) : CodeSigning.generate(commonName);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("BAD_SIGNING_KEY", e.getMessage());
        }
    }

    @Transactional
    public OtaApp rename(OtaApp app, String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty() || n.length() > 100) throw ApiException.badRequest("BAD_NAME", "名称不能为空，最多 100 字");
        app.setName(n);
        return apps.save(app);
    }

    /** 下载地址：http(s) 开头，留空表示没有 */
    @Transactional
    public OtaApp setDownloadUrl(OtaApp app, String url) {
        app.setDownloadUrl(downloadUrl(url));
        return apps.save(app);
    }

    public static String downloadUrl(String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) return null;
        if (u.length() > 500) throw ApiException.badRequest("BAD_DOWNLOAD_URL", "下载链接最多 500 个字符");
        if (!u.matches("(?i)^https?://[^\\s]+$")) throw ApiException.badRequest("BAD_DOWNLOAD_URL", "下载链接要以 http:// 或 https:// 开头");
        return u;
    }

    @Transactional
    public OtaApp setRedeemOpen(OtaApp app, boolean open) {
        app.setRedeemOpen(open);
        return apps.save(app);
    }

    @Transactional
    public OtaApp setPatchEnabled(OtaApp app, boolean enabled) {
        app.setPatchEnabled(enabled);
        return apps.save(app);
    }

    @Transactional
    public OtaApp setDedupeDevices(OtaApp app, boolean enabled) {
        app.setDedupeDevices(enabled);
        return apps.save(app);
    }

    @Transactional
    public OtaApp setFingerprintKeepDays(OtaApp app, int days) {
        app.setFingerprintKeepDays(Math.max(0, days));
        return apps.save(app);
    }

    /** 设置 Webhook。地址为空表示关闭；第一次设置时生成签名密钥 */
    @Transactional
    public OtaApp setWebhook(OtaApp app, String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) {
            app.setWebhookUrl(null);
            return apps.save(app);
        }
        try {
            URI uri = URI.create(u);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_WEBHOOK", "Webhook 地址要是 http 或 https 链接");
        }
        if (u.length() > 500) throw ApiException.badRequest("BAD_WEBHOOK", "Webhook 地址太长");
        app.setWebhookUrl(u);
        if (app.getWebhookSecretEnc() == null) app.setWebhookSecretEnc(vault.encrypt("whsec_" + vault.randomToken(24)));
        return apps.save(app);
    }

    /** 群机器人地址，空表示关闭 */
    @Transactional
    public OtaApp setNotify(OtaApp app, String url) {
        String u = url == null ? "" : url.trim();
        if (u.isEmpty()) {
            app.setNotifyUrl(null);
            return apps.save(app);
        }
        try {
            URI uri = URI.create(u);
            if (!("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (Exception e) {
            throw ApiException.badRequest("BAD_NOTIFY", "机器人地址要是 http 或 https 链接");
        }
        if (u.length() > 500) throw ApiException.badRequest("BAD_NOTIFY", "机器人地址太长");
        app.setNotifyUrl(u);
        return apps.save(app);
    }

    @Transactional
    public void recordNotifyResult(Long appId, String status) {
        apps.findById(appId).ifPresent(a -> {
            a.setNotifyLastStatus(status.length() > 300 ? status.substring(0, 300) : status);
            apps.save(a);
        });
    }

    public record SigningMaterial(String keyId, String privateKeyPem, String certificatePem) {}

    /** 签名密钥原文，复用到别的 App 时用 */
    public SigningMaterial signingMaterial(OtaApp app) {
        return new SigningMaterial(app.getKeyId(), vault.decrypt(app.getPrivateKeyEnc()), app.getCertificatePem());
    }

    @Transactional
    public OtaApp rotateWebhookSecret(OtaApp app) {
        app.setWebhookSecretEnc(vault.encrypt("whsec_" + vault.randomToken(24)));
        return apps.save(app);
    }

    @Transactional
    public OtaApp rotateCodeSecret(OtaApp app) {
        if (app.getCodeKey() == null) app.setCodeKey("app_" + vault.randomToken(12));
        app.setCodeSecretEnc(vault.encrypt(vault.randomToken(24)));
        codeSecretCache.remove(app.getId());
        return apps.save(app);
    }

    /** 从备份恢复口令接口、授权签名和 Webhook 的密钥：已发布的 App 和用户手上的授权用的是备份时的这套 */
    @Transactional
    public OtaApp restoreSecrets(OtaApp app, String codeKey, String codeSecret, String grantKey, String webhookUrl, String webhookSecret) {
        if (codeKey != null && codeSecret != null) {
            apps.findByCodeKey(codeKey).filter(o -> !o.getId().equals(app.getId())).ifPresent(o -> {
                throw ApiException.conflict("CODE_KEY_TAKEN", "口令接口的 App Key 已经被 App " + o.getSlug() + " 使用");
            });
            app.setCodeKey(codeKey);
            app.setCodeSecretEnc(vault.encrypt(codeSecret));
            codeSecretCache.remove(app.getId());
        }
        if (grantKey != null) {
            app.setGrantKeyEnc(vault.encrypt(grantKey));
            grantKeyCache.remove(app.getId());
        }
        app.setWebhookUrl(webhookUrl);
        app.setWebhookSecretEnc(webhookSecret == null ? null : vault.encrypt(webhookSecret));
        return apps.save(app);
    }

    /** 授权签名密钥的原文（Base64），备份用；老数据没有时补一把 */
    @Transactional
    public String grantKeyText(OtaApp app) {
        OtaApp fresh = apps.findById(app.getId()).orElse(app);
        if (fresh.getGrantKeyEnc() == null) {
            fresh.setGrantKeyEnc(vault.encrypt(vault.randomToken(32)));
            apps.save(fresh);
        }
        return vault.decrypt(fresh.getGrantKeyEnc());
    }

    public byte[] grantKey(OtaApp app) {
        return grantKeyCache.computeIfAbsent(app.getId(), id -> Base64.getUrlDecoder().decode(grantKeyText(app)));
    }

    @Transactional
    public void recordWebhookResult(Long appId, String status) {
        apps.findById(appId).ifPresent(a -> {
            a.setWebhookLastStatus(status.length() > 300 ? status.substring(0, 300) : status);
            a.setWebhookLastAt(Instant.now());
            apps.save(a);
        });
    }

    /** 老数据补齐口令接口的 Key 和密钥 */
    @Transactional
    public OtaApp ensureCodeKey(OtaApp app) {
        if (app.getCodeKey() != null && app.getCodeSecretEnc() != null) return app;
        return rotateCodeSecret(app);
    }

    public Optional<OtaApp> find(String slug) {
        return slug == null ? Optional.empty() : apps.findBySlug(slug.trim());
    }

    public Optional<OtaApp> findById(Long id) {
        return id == null ? Optional.empty() : apps.findById(id);
    }

    public Optional<OtaApp> findByCodeKey(String codeKey) {
        return codeKey == null || codeKey.isBlank() ? Optional.empty() : apps.findByCodeKey(codeKey.trim());
    }

    public OtaApp require(String slug) {
        return find(slug).orElseThrow(() -> ApiException.notFound("APP_NOT_FOUND", "App " + slug + " 不存在"));
    }

    public List<OtaApp> all() {
        return apps.findAllByOrderByCreatedAtAsc();
    }

    public PrivateKey privateKey(OtaApp app) {
        return keyCache.computeIfAbsent(app.getId(), id -> PemSupport.parsePrivateKey(vault.decrypt(app.getPrivateKeyEnc())));
    }

    public String codeSecret(OtaApp app) {
        if (app.getCodeSecretEnc() == null) return null;
        return codeSecretCache.computeIfAbsent(app.getId(), id -> vault.decrypt(app.getCodeSecretEnc()));
    }

    public String webhookSecret(OtaApp app) {
        return app.getWebhookSecretEnc() == null ? null : vault.decrypt(app.getWebhookSecretEnc());
    }

    @Transactional
    public Channel createChannel(OtaApp app, String name) {
        String n = name == null ? "" : name.trim();
        if (!CHANNEL.matcher(n).matches()) {
            throw ApiException.badRequest("BAD_CHANNEL", "渠道名只能用小写字母、数字和 . _ -，最多 40 位");
        }
        if (channels.findByAppIdAndName(app.getId(), n).isPresent()) {
            throw ApiException.conflict("CHANNEL_EXISTS", "渠道 " + n + " 已存在");
        }
        return channels.save(new Channel(app.getId(), n));
    }

    /** 删除渠道和它上面的发布记录（版本本身不删）。默认渠道 production 不能删 */
    @Transactional
    public void deleteChannel(OtaApp app, String name) {
        Channel ch = requireChannel(app, name);
        if (DEFAULT_CHANNEL.equals(ch.getName())) {
            throw ApiException.badRequest("CHANNEL_DEFAULT", "production 是默认渠道，不能删除");
        }
        deployments.deleteByChannelId(ch.getId());
        channels.delete(ch);
    }

    public long deploymentCount(Channel channel) {
        return deployments.countByChannelId(channel.getId());
    }

    /** App 删除后清掉内存里的密钥 */
    void evict(Long appId) {
        keyCache.remove(appId);
        codeSecretCache.remove(appId);
        grantKeyCache.remove(appId);
    }

    public Optional<Channel> findChannel(OtaApp app, String name) {
        return channels.findByAppIdAndName(app.getId(), name);
    }

    public Channel requireChannel(OtaApp app, String name) {
        return findChannel(app, name).orElseThrow(() -> ApiException.notFound("CHANNEL_NOT_FOUND", "渠道 " + name + " 不存在"));
    }

    public List<Channel> channels(OtaApp app) {
        return channels.findByAppIdOrderByCreatedAtAsc(app.getId());
    }

    /** 所有 App 的渠道，同名的只留一个（口令绑定渠道按名字，哪个 App 都认） */
    public List<Channel> channelNames() {
        java.util.Map<String, Channel> byName = new java.util.TreeMap<>((a, b) -> "production".equals(a) ? ("production".equals(b) ? 0 : -1)
                : "production".equals(b) ? 1 : a.compareTo(b));
        for (Channel c : channels.findAll()) byName.putIfAbsent(c.getName(), c);
        return new java.util.ArrayList<>(byName.values());
    }

    public static boolean isChannelName(String name) {
        return name != null && CHANNEL.matcher(name).matches();
    }
}
