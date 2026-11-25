package com.ota.platform.signing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/** PEM 私钥 / 证书解析。 */
public final class PemSupport {

    private PemSupport() {}

    /** 支持 PKCS#8（BEGIN PRIVATE KEY）和 PKCS#1（BEGIN RSA PRIVATE KEY）的 RSA 私钥 */
    public static PrivateKey parsePrivateKey(String pem) {
        if (pem.contains("ENCRYPTED PRIVATE KEY") || pem.contains("Proc-Type: 4,ENCRYPTED")) {
            throw new IllegalArgumentException("私钥带密码保护，请先解密");
        }
        boolean pkcs1 = pem.contains("BEGIN RSA PRIVATE KEY");
        if (!pkcs1 && !pem.contains("BEGIN PRIVATE KEY")) {
            throw new IllegalArgumentException("不是 PEM 格式的私钥（应以 -----BEGIN PRIVATE KEY----- 开头）");
        }
        try {
            byte[] der = Base64.getDecoder().decode(pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", ""));
            if (pkcs1) der = wrapPkcs1(der);
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析私钥，只支持 RSA 私钥");
        }
    }

    public static X509Certificate parseCertificate(String pem) {
        try {
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(pem.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception e) {
            throw new IllegalArgumentException("不是有效的证书（应以 -----BEGIN CERTIFICATE----- 开头）");
        }
    }

    /** RSA 私钥对应的公钥 */
    public static java.security.PublicKey publicKeyOf(PrivateKey key) throws Exception {
        if (!(key instanceof java.security.interfaces.RSAPrivateCrtKey rsa)) throw new IllegalArgumentException("只支持 RSA 私钥");
        return KeyFactory.getInstance("RSA").generatePublic(new java.security.spec.RSAPublicKeySpec(rsa.getModulus(), rsa.getPublicExponent()));
    }

    /** SHA-256 指纹，冒号分隔的十六进制 */
    public static String fingerprint(byte[] der) {
        byte[] d = com.ota.platform.common.Hashes.sha256().digest(der);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < d.length; i++) {
            if (i > 0) sb.append(':');
            sb.append(String.format("%02X", d[i]));
        }
        return sb.toString();
    }

    public static String toPem(String type, byte[] der) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(der);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }

    /** PKCS#1 RSAPrivateKey → PKCS#8 PrivateKeyInfo */
    private static byte[] wrapPkcs1(byte[] pkcs1) {
        byte[] version = {0x02, 0x01, 0x00};
        byte[] algorithm = {0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00};
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.writeBytes(version);
        body.writeBytes(algorithm);
        body.writeBytes(tlv(0x04, pkcs1));
        return tlv(0x30, body.toByteArray());
    }

    private static byte[] tlv(int tag, byte[] value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(tag);
        int len = value.length;
        if (len < 0x80) {
            out.write(len);
        } else {
            int bytes = len > 0xFFFFFF ? 4 : len > 0xFFFF ? 3 : len > 0xFF ? 2 : 1;
            out.write(0x80 | bytes);
            for (int i = bytes - 1; i >= 0; i--) out.write((len >> (8 * i)) & 0xFF);
        }
        out.writeBytes(value);
        return out.toByteArray();
    }
}
