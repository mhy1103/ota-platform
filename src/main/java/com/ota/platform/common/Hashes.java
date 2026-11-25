package com.ota.platform.common;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.regex.Pattern;

public final class Hashes {

    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");

    private Hashes() {}

    public static boolean isSha256Hex(String s) {
        return s != null && SHA256_HEX.matcher(s).matches();
    }

    public static MessageDigest sha256() {
        return digest("SHA-256");
    }

    public static MessageDigest md5() {
        return digest("MD5");
    }

    public static String sha256Hex(String s) {
        return hex(sha256().digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    public static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    /** Expo 清单里的 hash 字段：SHA-256 的 base64url，无填充 */
    public static String base64UrlOfHex(String hex) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(HexFormat.of().parseHex(hex));
    }

    /** base64url（清单里的 hash）转回十六进制 */
    public static String hexOfBase64Url(String b64) {
        return hex(Base64.getUrlDecoder().decode(b64));
    }

    public static byte[] hmacSha256(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String hmacSha256Hex(byte[] key, String data) {
        return hex(hmacSha256(key, data.getBytes(StandardCharsets.UTF_8)));
    }

    /** 常量时间比较，校验签名用 */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
