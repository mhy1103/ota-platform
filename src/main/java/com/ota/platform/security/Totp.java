package com.ota.platform.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Locale;

/** 动态验证码（TOTP，RFC 6238）：6 位、30 秒一换，和 Google / 微软身份验证器、1Password 等通用 */
public final class Totp {

    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    public static final int STEP_SECONDS = 30;

    private Totp() {}

    public static String newSecret() {
        byte[] b = new byte[20];
        RANDOM.nextBytes(b);
        return base32(b);
    }

    /** 当前时间所在的时间片 */
    public static long step(long epochMillis) {
        return epochMillis / 1000 / STEP_SECONDS;
    }

    /** 返回验证通过的时间片（前后各容忍一个，防手机时间略有偏差）；不通过返回 -1 */
    public static long match(String secret, String code, long epochMillis) {
        String c = code == null ? "" : code.replaceAll("\\s", "");
        if (!c.matches("\\d{6}")) return -1;
        byte[] key = unbase32(secret);
        long now = step(epochMillis);
        for (long s = now - 1; s <= now + 1; s++) {
            if (constantEquals(code(key, s), c)) return s;
        }
        return -1;
    }

    public static String code(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int off = h[h.length - 1] & 0x0f;
            int bin = ((h[off] & 0x7f) << 24) | ((h[off + 1] & 0xff) << 16) | ((h[off + 2] & 0xff) << 8) | (h[off + 3] & 0xff);
            return String.format("%06d", bin % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public static String uri(String issuer, String account, String secret) {
        return "otpauth://totp/" + enc(issuer) + ":" + enc(account) + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=6&period=" + STEP_SECONDS;
    }

    /** 每 4 位一组显示，方便手动输入 */
    public static String grouped(String secret) {
        return secret.replaceAll("(.{4})(?!$)", "$1 ");
    }

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                sb.append(B32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) sb.append(B32.charAt((buffer << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] unbase32(String s) {
        String t = s.replaceAll("[\\s=-]", "").toUpperCase(Locale.ROOT);
        ByteBuffer out = ByteBuffer.allocate(t.length() * 5 / 8);
        int buffer = 0, bits = 0;
        for (char ch : t.toCharArray()) {
            int v = B32.indexOf(ch);
            if (v < 0) throw new IllegalArgumentException("bad base32");
            buffer = (buffer << 5) | v;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        byte[] r = new byte[out.position()];
        out.flip();
        out.get(r);
        return r;
    }

    private static boolean constantEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int r = 0;
        for (int i = 0; i < a.length(); i++) r |= a.charAt(i) ^ b.charAt(i);
        return r == 0;
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
