package com.ota.platform.signing;

import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.config.OtaProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateCrtKey;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** 检查一个 App 的签名配置：私钥能否解开、证书用途和有效期、两者是否配对、能否签名验签。 */
@Component
public class SigningInspector {

    public static final String ALGORITHM = "rsa-v1_5-sha256";
    private static final String CODE_SIGNING_OID = "1.3.6.1.5.5.7.3.3";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AppService apps;
    private final ZoneId zone;

    public SigningInspector(AppService apps, OtaProperties props) {
        this.apps = apps;
        this.zone = ZoneId.of(props.getDisplayZone());
    }

    public Status inspect(OtaApp app) {
        Status s = new Status();
        s.keyId = app.getKeyId();
        s.keyUpdatedAt = TIME.format(app.getKeyUpdatedAt().atZone(zone));
        PublicKey keyPublic = null;
        PrivateKey key = null;
        try {
            key = apps.privateKey(app);
            keyPublic = PemSupport.publicKeyOf(key);
            s.keyLoaded = true;
            s.keyBits = ((RSAPrivateCrtKey) key).getModulus().bitLength();
            s.keyFingerprint = PemSupport.fingerprint(keyPublic.getEncoded());
        } catch (Exception e) {
            s.keyError = e.getMessage();
        }
        X509Certificate cert = null;
        try {
            cert = PemSupport.parseCertificate(app.getCertificatePem());
            describe(cert, s);
            s.certOk = true;
        } catch (Exception e) {
            s.certError = e.getMessage();
        }
        if (keyPublic != null && cert != null) s.matched = Arrays.equals(keyPublic.getEncoded(), cert.getPublicKey().getEncoded());
        if (key != null && keyPublic != null) s.selfTest = selfTest(key, cert != null && Boolean.TRUE.equals(s.matched) ? cert.getPublicKey() : keyPublic);
        s.finish();
        return s;
    }

    private void describe(X509Certificate cert, Status s) throws Exception {
        s.subject = cert.getSubjectX500Principal().getName();
        s.issuer = cert.getIssuerX500Principal().getName();
        s.selfSigned = s.subject.equals(s.issuer);
        LocalDate from = LocalDate.ofInstant(cert.getNotBefore().toInstant(), zone);
        LocalDate to = LocalDate.ofInstant(cert.getNotAfter().toInstant(), zone);
        s.notBefore = DAY.format(from);
        s.notAfter = DAY.format(to);
        Instant now = Instant.now();
        s.expired = now.isAfter(cert.getNotAfter().toInstant());
        s.notYetValid = now.isBefore(cert.getNotBefore().toInstant());
        s.daysLeft = ChronoUnit.DAYS.between(LocalDate.now(zone), to);
        boolean[] ku = cert.getKeyUsage();
        s.digitalSignature = ku != null && ku.length > 0 && ku[0];
        List<String> eku = cert.getExtendedKeyUsage();
        s.codeSigning = eku != null && eku.contains(CODE_SIGNING_OID);
        s.certKeyFingerprint = PemSupport.fingerprint(cert.getPublicKey().getEncoded());
    }

    private static boolean selfTest(PrivateKey key, PublicKey pub) {
        try {
            byte[] data = ("ota-signing-self-test-" + System.nanoTime()).getBytes(StandardCharsets.UTF_8);
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(data);
            byte[] sig = signer.sign();
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(pub);
            verifier.update(data);
            return verifier.verify(sig);
        } catch (Exception e) {
            return false;
        }
    }

    public static class Check {
        private final String label;
        private final String state;
        private final String text;

        Check(String label, String state, String text) {
            this.label = label;
            this.state = state;
            this.text = text;
        }

        public String getLabel() { return label; }
        /** ok / warn / error / skip */
        public String getState() { return state; }
        public String getText() { return text; }
    }

    public static class Status {
        private boolean keyLoaded, certOk, selfSigned, expired, notYetValid, digitalSignature, codeSigning;
        private String keyId, keyError, keyUpdatedAt, keyFingerprint;
        private int keyBits;
        private String certError, subject, issuer, notBefore, notAfter, certKeyFingerprint;
        private long daysLeft;
        private Boolean matched, selfTest;
        private String level = "error";
        private String headline = "签名私钥无法解开";
        private String detail = "";
        private final List<Check> checks = new ArrayList<>();

        void finish() {
            checks.add(keyLoaded ? new Check("私钥", "ok", "已加密保存 · RSA " + keyBits + " 位")
                    : new Check("私钥", "error", "无法解开：" + keyError + "（主密钥和加密时用的不一致？）"));
            checks.add(certOk ? new Check("证书", "ok", cn(subject))
                    : new Check("证书", "error", "无法解析：" + certError));
            checks.add(matched == null ? new Check("私钥与证书配对", "skip", "需要私钥和证书都正常")
                    : matched ? new Check("私钥与证书配对", "ok", "公钥指纹一致，是同一对密钥")
                    : new Check("私钥与证书配对", "error", "不配对，App 会拒绝所有更新"));
            if (certOk) {
                checks.add(digitalSignature && codeSigning ? new Check("证书用途", "ok", "数字签名 · 代码签名")
                        : new Check("证书用途", "error", "缺少" + (digitalSignature ? "" : "「数字签名」") + (codeSigning ? "" : "「代码签名」")
                        + "用途，App 不接受这张证书"));
                checks.add(expired ? new Check("有效期", "error", "已于 " + notAfter + " 过期")
                        : notYetValid ? new Check("有效期", "warn", notBefore + " 才生效")
                        : daysLeft < 90 ? new Check("有效期", "warn", "剩余 " + daysLeft + " 天（到 " + notAfter + "）")
                        : new Check("有效期", "ok", "剩余 " + String.format("%,d", daysLeft) + " 天（到 " + notAfter + "）"));
            } else {
                checks.add(new Check("证书用途", "skip", "证书无法解析"));
                checks.add(new Check("有效期", "skip", "证书无法解析"));
            }
            checks.add(selfTest == null ? new Check("签名自检", "skip", "需要先解开私钥")
                    : selfTest ? new Check("签名自检", "ok", "用私钥签名、证书验签通过")
                    : new Check("签名自检", "error", "签名或验签失败"));

            if (!keyLoaded) {
                level = "error";
                headline = "签名私钥无法解开";
                detail = "更新清单签不了名，App 会拒绝所有更新。检查主密钥（master.key 或 OTA_MASTER_KEY）是否和当初一致。";
            } else if (Boolean.FALSE.equals(matched)) {
                level = "error";
                headline = "私钥与证书不配对";
                detail = "App 用内置证书验证签名，不配对时所有更新都会被拒绝。";
            } else if (Boolean.FALSE.equals(selfTest)) {
                level = "error";
                headline = "签名自检失败";
                detail = "私钥无法完成签名，请重新上传私钥。";
            } else if (certOk && (expired || !digitalSignature || !codeSigning)) {
                level = "error";
                headline = expired ? "证书已过期" : "证书缺少代码签名用途";
                detail = "这张证书不能用于验证更新。";
            } else if (notYetValid || daysLeft < 90) {
                level = "warn";
                headline = notYetValid ? "证书尚未生效" : "证书即将过期";
                detail = "证书有效期到 " + notAfter + "。证书编进了 App，过期前需要用新证书重新打包 App。";
            } else {
                level = "ok";
                headline = "签名正常";
                detail = "私钥与证书配对，签名自检通过。更新清单会用 keyid「" + keyId + "」签名。";
            }
        }

        private static String cn(String dn) {
            if (dn == null) return "—";
            for (String part : dn.split(",")) {
                String p = part.trim();
                if (p.startsWith("CN=")) return p.substring(3);
            }
            return dn;
        }

        /** 顶栏用的短文本 */
        public String getChip() {
            if ("ok".equals(level)) return "签名正常";
            return "error".equals(level) ? "签名异常" : "签名待注意";
        }

        public String getCommonName() { return cn(subject); }
        public String getLevel() { return level; }
        public String getHeadline() { return headline; }
        public String getDetail() { return detail; }
        public List<Check> getChecks() { return checks; }
        public boolean isKeyLoaded() { return keyLoaded; }
        public boolean isCertOk() { return certOk; }
        public String getKeyError() { return keyError; }
        public String getKeyUpdatedAt() { return keyUpdatedAt; }
        public String getKeyFingerprint() { return keyFingerprint; }
        public int getKeyBits() { return keyBits; }
        public String getCertError() { return certError; }
        public String getSubject() { return subject; }
        public String getIssuer() { return issuer; }
        public boolean isSelfSigned() { return selfSigned; }
        public String getNotBefore() { return notBefore; }
        public String getNotAfter() { return notAfter; }
        public long getDaysLeft() { return daysLeft; }
        public boolean isExpired() { return expired; }
        public boolean isDigitalSignature() { return digitalSignature; }
        public boolean isCodeSigning() { return codeSigning; }
        public String getCertKeyFingerprint() { return certKeyFingerprint; }
        public Boolean getMatched() { return matched; }
        public Boolean getSelfTest() { return selfTest; }
        public String getKeyId() { return keyId; }
        public String getAlgorithm() { return ALGORITHM; }
    }
}
