package com.ota.platform.signing;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;

/**
 * expo-updates 的代码签名：清单用 RSA-SHA256 签名，App 用内置证书验签。
 * 证书要求：密钥用途包含数字签名，扩展用途包含代码签名，并且在有效期内。
 */
public final class CodeSigning {

    private static final String CODE_SIGNING_OID = "1.3.6.1.5.5.7.3.3";

    private CodeSigning() {}

    public record Material(String privateKeyPem, String certificatePem) {}

    /** 生成 RSA 2048 密钥和自签名证书，有效期 20 年 */
    public static Material generate(String commonName) {
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair pair = gen.generateKeyPair();
            X500Name subject = new X500Name("CN=" + commonName.replaceAll("[,=+<>#;\"\\\\]", " ").trim());
            Instant now = Instant.now();
            var builder = new JcaX509v3CertificateBuilder(
                    subject,
                    new BigInteger(64, new SecureRandom()).abs().add(BigInteger.ONE),
                    Date.from(now.minus(Duration.ofDays(1))),
                    Date.from(now.plus(Duration.ofDays(365L * 20))),
                    subject,
                    pair.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
            builder.addExtension(Extension.extendedKeyUsage, true, new ExtendedKeyUsage(KeyPurposeId.id_kp_codeSigning));
            var holder = builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(pair.getPrivate()));
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(holder);
            return new Material(
                    PemSupport.toPem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                    PemSupport.toPem("CERTIFICATE", cert.getEncoded()));
        } catch (Exception e) {
            throw new IllegalStateException("生成签名密钥失败", e);
        }
    }

    /** 检查导入的私钥和证书能配对，且证书满足 expo-updates 的要求；返回规范化后的 PEM */
    public static Material validate(String privateKeyPem, String certificatePem) {
        PrivateKey key = PemSupport.parsePrivateKey(privateKeyPem);
        X509Certificate cert = PemSupport.parseCertificate(certificatePem);
        try {
            cert.checkValidity();
        } catch (Exception e) {
            throw new IllegalArgumentException("证书不在有效期内");
        }
        boolean[] usage = cert.getKeyUsage();
        if (usage != null && !usage[0]) throw new IllegalArgumentException("证书的密钥用途缺少「数字签名」");
        List<String> eku;
        try {
            eku = cert.getExtendedKeyUsage();
        } catch (Exception e) {
            eku = null;
        }
        if (eku == null || !eku.contains(CODE_SIGNING_OID)) throw new IllegalArgumentException("证书的扩展用途缺少「代码签名」");
        byte[] probe = "ota-platform-key-check".getBytes(StandardCharsets.UTF_8);
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(cert.getPublicKey());
            verifier.update(probe);
            if (!verifier.verify(Base64.getDecoder().decode(sign(key, probe)))) {
                throw new IllegalArgumentException("私钥和证书不是同一对");
            }
            return new Material(PemSupport.toPem("PRIVATE KEY", key.getEncoded()), PemSupport.toPem("CERTIFICATE", cert.getEncoded()));
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("私钥和证书校验失败：" + e.getMessage());
        }
    }

    /** RSA-SHA256 签名，返回 Base64（expo-signature 的 sig 字段） */
    public static String sign(PrivateKey key, byte[] data) {
        try {
            Signature signer = Signature.getInstance("SHA256withRSA");
            signer.initSign(key);
            signer.update(data);
            return Base64.getEncoder().encodeToString(signer.sign());
        } catch (Exception e) {
            throw new IllegalStateException("签名失败", e);
        }
    }
}
