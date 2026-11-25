package com.ota.platform.codes;

import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.webhook.WebhookService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import com.ota.platform.common.Hashes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 口令兑换和激活确认，规则：
 * B 面（兑换开关打开）才能兑换；口令要存在、启用、未过期、未达上限；
 * 口令所有 App 通用；兑换后是「待确认」，App 启动成功后确认才计入有效激活。
 * 同一 App 里一台设备只算一次激活：再兑换同一口令，或者已经用别的口令激活过，都沿用原来那次激活，不新增记录、不占口令用量。
 * 所有 App 加起来也只算一次：确认时，这台手机的设备指纹（/var/mobile 初始化时间 + 机型，跨 App 跨账号一样）
 * 如果别处已经算过一次有效激活，这条记成「重复」，不算有效激活、不占口令次数；B 包校验时顺带把指纹补写到老记录上，
 * 升级前就激活的用户也救得回来。兑换后不到 ota.min-confirm-ms 就来确认的判为刷的：标成「非正常」，以后怎么确认都不算。
 */
@Service
public class CodeService {

    public enum Outcome {
        ISSUED, REISSUED, PAUSED, NOT_FOUND, DISABLED, EXPIRED, EXHAUSTED;

        public boolean ok() {
            return this == ISSUED || this == REISSUED;
        }

        public String error() {
            return switch (this) {
                case PAUSED -> "REDEMPTION_PAUSED";
                case NOT_FOUND -> "CODE_NOT_FOUND";
                case DISABLED -> "CODE_DISABLED";
                case EXPIRED -> "CODE_EXPIRED";
                case EXHAUSTED -> "CODE_EXHAUSTED";
                default -> null;
            };
        }

        public String message() {
            return switch (this) {
                case ISSUED -> "兑换成功";
                case REISSUED -> "这台设备已经激活过，重新签发原来的授权";
                case PAUSED -> "暂停兑换中";
                case NOT_FOUND -> "口令不存在";
                case DISABLED -> "口令已停用";
                case EXPIRED -> "口令已过期";
                case EXHAUSTED -> "口令已达到使用上限";
            };
        }
    }

    public record Redeemed(Outcome outcome, ActivationCode code, ActivationRecord record, String deviceId) {}

    private final ActivationCodeRepo codeRepo;
    private final ActivationRecordRepo recordRepo;
    private final AuditService audit;
    private final WebhookService webhooks;
    private final com.ota.platform.web.agent.CodeLimits limits;
    private final OtaProperties props;
    private final TransactionTemplate tx;
    private final Object[] stripes = new Object[64];

    public CodeService(ActivationCodeRepo codeRepo, ActivationRecordRepo recordRepo, AuditService audit, WebhookService webhooks,
                       com.ota.platform.web.agent.CodeLimits limits, OtaProperties props, PlatformTransactionManager txManager) {
        this.codeRepo = codeRepo;
        this.recordRepo = recordRepo;
        this.audit = audit;
        this.webhooks = webhooks;
        this.limits = limits;
        this.props = props;
        this.tx = new TransactionTemplate(txManager);
        for (int i = 0; i < stripes.length; i++) stripes[i] = new Object();
    }

    /**
     * 同一设备连点两次：同一台机器上排队，第二次直接读到第一次的记录，按「已兑换」返回。
     * 多实例部署时由唯一约束兜底，撞上了换个事务重读一遍，同样不报 500。
     */
    public Redeemed redeem(OtaApp app, String codeText, String deviceId, String ip) {
        String key = app.getId() + "|" + (codeText == null ? "" : codeText.trim()) + "|" + (deviceId == null ? "" : deviceId.trim());
        synchronized (stripes[Math.floorMod(key.hashCode(), stripes.length)]) {
            try {
                return tx.execute(s -> doRedeem(app, codeText, deviceId, ip));
            } catch (DataIntegrityViolationException e) {
                return tx.execute(s -> doRedeem(app, codeText, deviceId, ip));
            }
        }
    }

    /** App 报上来的手机型号（比如 iPhone15,2）；不像型号的不要 */
    public static String model(String raw) {
        if (raw == null) return null;
        String m = raw.trim();
        return m.isEmpty() || m.length() > 40 || !m.matches("[A-Za-z0-9,._ ()-]+") ? null : m;
    }

    /** 设备指纹和它是怎么算出来的：fp = /var/mobile 初始化时间 + 机型；ip = 读不到初始化时间时用 IP + 机型兜底 */
    public record Fp(String value, String by) {}

    /**
     * 算设备指纹。首选 /var/mobile 初始化时间 + 机型：不同开发者账号的 App 读到的初始化时间一样，所以同一台手机算出来一样，
     * 跨 App、跨账号认得出。读不到初始化时间时，用公网 IP + 机型兜底（弱一些，换了网络就对不上）。都没有返回 null。
     */
    public static Fp deviceFp(String initTime, String model, String ip) {
        String m = model(model);
        String t = initTime == null ? "" : initTime.trim();
        if (!t.isEmpty() && t.length() <= 40 && t.matches("[0-9.]+")) {
            return new Fp(Hashes.sha256Hex("fpv1|" + t + "|" + (m == null ? "" : m)).substring(0, 40), "fp");
        }
        if (m != null && ip != null && !AuditService.privateIp(ip)) {
            return new Fp(Hashes.sha256Hex("fbv1|" + ip.trim() + "|" + m).substring(0, 40), "ip");
        }
        return null;
    }

    private Redeemed doRedeem(OtaApp app, String codeText, String deviceId, String ip) {
        if (!app.isRedeemOpen()) return new Redeemed(Outcome.PAUSED, null, null, null);
        String content = codeText == null ? "" : codeText.trim();
        Optional<ActivationCode> codeOpt = content.isEmpty() ? Optional.empty() : codeRepo.findByCode(content);
        if (codeOpt.isEmpty()) return new Redeemed(Outcome.NOT_FOUND, null, null, null);
        ActivationCode code = codeOpt.get();
        if (!limits.canActivate(code)) {
            Outcome why = !Boolean.TRUE.equals(code.getEnabled()) ? Outcome.DISABLED
                    : code.getExpiresAt() != null && LocalDateTime.now().isAfter(code.getExpiresAt()) ? Outcome.EXPIRED
                    : Outcome.EXHAUSTED;
            return new Redeemed(why, code, null, null);
        }

        String rawDevice = deviceId == null ? "" : deviceId.trim();
        if (rawDevice.length() > 200) rawDevice = rawDevice.substring(0, 200);
        if (!rawDevice.isEmpty()) {
            Optional<ActivationRecord> existing = recordRepo.findByAppIdAndDeviceIdAndCodeId(app.getId(), rawDevice, code.getId());
            if (existing.isPresent()) return new Redeemed(Outcome.REISSUED, code, existing.get(), rawDevice);
            // 已经用别的口令激活过：授权按原来的口令签发，之后的确认也落在原来那条记录上，这个口令不记录
            Optional<ActivationRecord> first = recordRepo.findFirstByAppIdAndDeviceIdAndConfirmedTrueOrderByIdAsc(app.getId(), rawDevice);
            Optional<ActivationCode> firstCode = first.flatMap(r -> codeRepo.findById(r.getCodeId()));
            if (firstCode.isPresent()) {
                audit.log("CODE_SUBMIT", "口令[" + code.getCode() + "] 设备:" + truncate(rawDevice, 12) + " 已用口令[" + firstCode.get().getCode()
                        + "]激活过，不再记录 · " + app.getSlug());
                return new Redeemed(Outcome.REISSUED, firstCode.get(), first.get(), rawDevice);
            }
        }
        String did = rawDevice.isEmpty() ? "anon-" + System.currentTimeMillis() : rawDevice;

        // 创建激活记录（confirmed=false，还不算有效激活），usedCount 等 App 启动成功确认后才加
        ActivationRecord record = new ActivationRecord();
        record.setAppId(app.getId());
        record.setCodeId(code.getId());
        record.setCodeText(code.getCode());
        record.setDeviceId(did);
        record.setDeviceIp(ip);
        record.setConfirmed(false);
        recordRepo.save(record);

        audit.log("CODE_SUBMIT", "口令[" + code.getCode() + "] 设备:" + truncate(did, 12) + " (待确认) · " + app.getSlug());
        webhooks.emit(app, "redemption.created", event(app, code, record));
        return new Redeemed(Outcome.ISSUED, code, record, did);
    }

    private static String dupNote(String code, String device, String by, Long ofId) {
        String how = switch (by) {
            case "fp" -> "同一台手机";
            case "ip" -> "同一个 IP、同机型（兜底）";
            default -> "同一个设备号";
        };
        return "口令[" + code + "] 设备:" + truncate(device, 12) + " 在别的 App 已经激活过（" + how + "，激活记录 #" + ofId + "），不算有效激活";
    }

    /** App 启动成功后调用：把这条激活记为有效，计入口令用量。fp 是算好的设备指纹。返回 true 表示这次才确认 */
    @Transactional
    public boolean confirm(OtaApp app, Long codeId, String deviceId, Fp fp) {
        Optional<ActivationRecord> found = recordRepo.findByAppIdAndDeviceIdAndCodeId(app.getId(), deviceId, codeId);
        if (found.isEmpty()) return false;
        ActivationRecord record = found.get();
        if (Boolean.TRUE.equals(record.getConfirmed()) || record.getFastMs() != null || record.getDupOf() != null) return false;
        if (recordRepo.existsByAppIdAndDeviceIdAndConfirmedTrueAndIdNot(app.getId(), deviceId, record.getId())) return false;
        String f = record.getFingerprint() != null ? record.getFingerprint() : fp == null ? null : fp.value();
        LocalDateTime now = LocalDateTime.now();
        // 非正常：兑换后不到 ota.min-confirm-ms 就来确认的（脚本刷的，真手机要先下载 B 面包再重启）
        if (record.getActivatedAt() != null) {
            long gap = Math.max(0, Duration.between(record.getActivatedAt(), now).toMillis());
            if (gap < props.getMinConfirmMs()) {
                if (recordRepo.markFast(record.getId(), (int) gap) > 0) {
                    audit.log("CONFIRM_FAST", "设备:" + truncate(deviceId, 12) + " 口令ID:" + codeId + " 兑换后 " + gap + " 毫秒就来确认，不算有效激活 · " + app.getSlug());
                }
                return false;
            }
        }
        // 重复：这个 App 开了「按设备去重」时，同一台手机（指纹，或同设备号的另一个 App）已经在别处算过一次有效激活
        if (app.isDedupeDevices()) {
            ActivationRecord other = null;
            String by = null;
            if (f != null) {
                other = recordRepo.findFirstByFingerprintAndConfirmedTrueAndIdNot(f, record.getId()).orElse(null);
                if (other != null) by = fp != null && fp.value().equals(f) ? fp.by() : "fp";
            }
            if (other == null) {
                other = recordRepo.findFirstByDeviceIdAndAppIdNotAndConfirmedTrueOrderByIdAsc(deviceId, app.getId()).orElse(null);
                if (other != null) by = "device";
            }
            if (other != null) {
                if (f != null && record.getFingerprint() == null) recordRepo.setFingerprintIfMissing(record.getId(), f);
                if (recordRepo.markDup(record.getId(), other.getId(), by) > 0) {
                    audit.log("CODE_DUP", dupNote(record.getCodeText(), deviceId, by, other.getId()) + " · " + app.getSlug());
                }
                return false;
            }
        }
        if (f != null && record.getFingerprint() == null) recordRepo.setFingerprintIfMissing(record.getId(), f);
        if (recordRepo.markConfirmed(record.getId(), now) == 0) return false;
        codeRepo.incrementUsed(codeId);
        record.setConfirmed(true);
        record.setConfirmedAt(now);
        Optional<ActivationCode> code = codeRepo.findById(codeId);
        audit.log("CONFIRMED", "设备:" + truncate(deviceId, 12) + " 口令ID:" + codeId + " · " + app.getSlug());
        code.ifPresent(c -> webhooks.emit(app, "redemption.confirmed", event(app, c, record)));
        code.ifPresent(c -> limits.touch(c.getAgentId()));
        return true;
    }

    /** 按授权确认：按口令内容找口令（恢复备份后口令 ID 会变） */
    @Transactional
    public boolean confirmGrant(OtaApp app, GrantService.Grant g, Fp fp) {
        Long codeId = codeIdOf(g);
        return codeId != null && confirm(app, codeId, g.deviceId(), fp);
    }

    /** 口令授权对应的激活记录；日活和指纹补写都用它定位（找不到返回 null） */
    public Long codeIdOf(GrantService.Grant g) {
        return codeRepo.findByCode(g.code()).map(ActivationCode::getId).orElse(null);
    }

    private static Map<String, Object> event(OtaApp app, ActivationCode code, ActivationRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("app", app.getSlug());
        m.put("code", code.getCode());
        m.put("codeId", code.getId());
        m.put("agentId", code.getAgentId());
        m.put("channel", code.getChannel());
        m.put("recordId", r.getId());
        m.put("deviceId", r.getDeviceId());
        m.put("ip", r.getDeviceIp());
        m.put("confirmed", Boolean.TRUE.equals(r.getConfirmed()));
        m.put("usedCount", code.getUsedCount());
        m.put("maxUses", code.getMaxUses());
        return m;
    }

    private static String truncate(String s, int len) {
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }
}
