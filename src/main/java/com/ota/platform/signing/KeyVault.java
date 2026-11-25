package com.ota.platform.signing;

import com.ota.platform.config.OtaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/** 用主密钥（AES-256-GCM）加密存储签名私钥。 */
@Component
public class KeyVault {

    private static final Logger log = LoggerFactory.getLogger(KeyVault.class);
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public KeyVault(OtaProperties props) throws IOException {
        byte[] raw;
        String configured = props.getMasterKey();
        if (configured != null && !configured.isBlank()) {
            raw = Base64.getDecoder().decode(configured.trim());
        } else {
            raw = loadOrCreate(Path.of(props.getDataDir(), "master.key"));
        }
        if (raw.length != 32) throw new IllegalStateException("ota.master-key 必须是 32 字节的 Base64");
        this.key = new SecretKeySpec(raw, "AES");
    }

    /** 用主密钥派生的 HMAC，给需要稳定、又不能被猜出来的标识用（比如浏览器缓存的分区名） */
    public String hmacHex(String data, int bytes) {
        try {
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getEncoded(), "HmacSHA256"));
            byte[] out = mac.doFinal(("ota:" + data).getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(out, 0, Math.min(bytes, out.length));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String randomToken(int bytes) {
        byte[] b = new byte[bytes];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public String encrypt(String plain) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) throw new IllegalStateException("无法识别的密文格式");
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("解密失败：主密钥和加密时用的不一致", e);
        }
    }

    private byte[] loadOrCreate(Path file) throws IOException {
        if (Files.isRegularFile(file)) {
            return Base64.getDecoder().decode(Files.readString(file).trim());
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        Files.createDirectories(file.getParent());
        Files.writeString(file, Base64.getEncoder().encodeToString(raw) + "\n");
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Windows 等不支持 POSIX 权限的文件系统
        }
        log.warn("已生成主密钥 {}。它用来加密各 App 的签名私钥，丢了就解不开，请备份；生产环境建议改用环境变量 OTA_MASTER_KEY。", file.toAbsolutePath());
        return raw;
    }
}
