package com.ota.platform.security;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.ota.platform.admin.AdminUser;
import com.ota.platform.admin.AdminUserRepo;
import com.ota.platform.common.ApiException;
import com.ota.platform.signing.KeyVault;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 两步验证：开启、关闭、登录时校验动态码或恢复码 */
@Service
public class TwoFactorService {

    public static final String ISSUER = "OTA 管理后台";
    private static final String CODE_CHARS = "abcdefghjkmnpqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminUserRepo users;
    private final KeyVault vault;
    /** 每个管理员最近一次用掉的时间片：同一个动态码不能用两次 */
    private final Map<Long, Long> lastStep = new ConcurrentHashMap<>();

    public TwoFactorService(AdminUserRepo users, KeyVault vault) {
        this.users = users;
        this.vault = vault;
    }

    public record Setup(String secret, String grouped, String uri, String qr) {}

    /** pending 是这次设置还没确认的密钥：输错动态码重试时沿用，不用重新扫码 */
    public Setup begin(String username, String host, String pending) {
        String secret = pending != null && pending.matches("[A-Z2-7]{32}") ? pending : Totp.newSecret();
        String account = host == null || host.isBlank() ? username : username + "@" + host;
        String uri = Totp.uri(ISSUER, account, secret);
        return new Setup(secret, Totp.grouped(secret), uri, svg(uri));
    }

    /** 用设置时生成的密钥校验第一次输入的动态码，通过就保存，返回恢复码（只显示这一次） */
    public List<String> enable(Long adminId, String secret, String code) {
        if (secret == null) throw ApiException.badRequest("TOTP_SETUP", "设置已过期，请重新开启");
        long s = Totp.match(secret, code, System.currentTimeMillis());
        if (s < 0) throw ApiException.badRequest("TOTP_CODE", "动态码不对，请确认手机时间准确后再输入");
        AdminUser u = users.findById(adminId).orElseThrow();
        u.setTotpSecret(vault.encrypt(secret));
        u.setTotpAt(Instant.now());
        List<String> codes = newCodes();
        u.setTotpRecovery(hashes(codes));
        users.save(u);
        lastStep.put(adminId, s);
        return codes;
    }

    public void disable(Long adminId) {
        AdminUser u = users.findById(adminId).orElseThrow();
        u.setTotpSecret(null);
        u.setTotpRecovery(null);
        u.setTotpAt(null);
        users.save(u);
        lastStep.remove(adminId);
    }

    public List<String> regenerate(Long adminId) {
        AdminUser u = users.findById(adminId).orElseThrow();
        if (!u.isTotpEnabled()) throw ApiException.badRequest("TOTP_OFF", "还没有开启两步验证");
        List<String> codes = newCodes();
        u.setTotpRecovery(hashes(codes));
        users.save(u);
        return codes;
    }

    /** 校验动态码（同一个码只能用一次） */
    public boolean verify(AdminUser u, String code) {
        if (!u.isTotpEnabled()) return false;
        String secret;
        try {
            secret = vault.decrypt(u.getTotpSecret());
        } catch (Exception e) {
            return false;
        }
        long s = Totp.match(secret, code, System.currentTimeMillis());
        if (s < 0) return false;
        Long used = lastStep.get(u.getId());
        if (used != null && s <= used) return false;
        lastStep.put(u.getId(), s);
        return true;
    }

    /** 校验恢复码，通过就作废这一个 */
    public synchronized boolean useRecovery(Long adminId, String code) {
        AdminUser u = users.findById(adminId).orElse(null);
        if (u == null || u.getTotpRecovery() == null || code == null) return false;
        String h = sha256(code.trim().toLowerCase(Locale.ROOT).replaceAll("[\\s]", ""));
        List<String> left = new ArrayList<>(Arrays.asList(u.getTotpRecovery().split(",")));
        if (!left.remove(h)) return false;
        u.setTotpRecovery(String.join(",", left));
        users.save(u);
        return true;
    }

    public int recoveryLeft(AdminUser u) {
        String r = u.getTotpRecovery();
        return r == null || r.isBlank() ? 0 : r.split(",").length;
    }

    private static List<String> newCodes() {
        List<String> out = new ArrayList<>();
        while (out.size() < 8) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10; i++) {
                if (i == 5) sb.append('-');
                sb.append(CODE_CHARS.charAt(RANDOM.nextInt(CODE_CHARS.length())));
            }
            if (!out.contains(sb.toString())) out.add(sb.toString());
        }
        return out;
    }

    private static String hashes(List<String> codes) {
        StringJoiner j = new StringJoiner(",");
        for (String c : codes) j.add(sha256(c));
        return j.toString();
    }

    private static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(("ota-recovery:" + s).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** 二维码画成 SVG（一行里连续的黑块合成一个矩形），页面里直接显示，不用外部服务 */
    public static String svg(String text) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 0);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, hints);
            int w = m.getWidth(), h = m.getHeight();
            StringBuilder p = new StringBuilder();
            for (int y = 0; y < h; y++) {
                int x = 0;
                while (x < w) {
                    if (!m.get(x, y)) { x++; continue; }
                    int start = x;
                    while (x < w && m.get(x, y)) x++;
                    p.append('M').append(start).append(' ').append(y).append('h').append(x - start).append("v1h-").append(x - start).append('z');
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"-2 -2 " + (w + 4) + " " + (h + 4) + "\" shape-rendering=\"crispEdges\">"
                    + "<rect x=\"-2\" y=\"-2\" width=\"" + (w + 4) + "\" height=\"" + (h + 4) + "\" fill=\"#fff\"/>"
                    + "<path fill=\"#0f0f11\" d=\"" + p + "\"/></svg>";
        } catch (Exception e) {
            throw new IllegalStateException("生成二维码失败", e);
        }
    }
}
