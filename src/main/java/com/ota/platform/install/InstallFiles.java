package com.ota.platform.install;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * install.json 的读写和其中密码的加解密。启动早期（还没有 Spring Bean）也要用，所以都是静态方法；
 * 密钥和 KeyVault 用的是同一个主密钥（环境变量 OTA_MASTER_KEY 或数据目录的 master.key），密文格式也一样。
 */
public final class InstallFiles {

    static final String FILE = "install.json";
    private static final String PREFIX = "v1:";
    private static final int IV_BYTES = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final ObjectMapper JSON = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private InstallFiles() {}

    public static Path file(String dataDir) {
        return Path.of(dataDir, FILE);
    }

    public static InstallConfig read(String dataDir) {
        Path f = file(dataDir);
        if (!Files.isRegularFile(f)) return null;
        try {
            return JSON.readValue(f.toFile(), InstallConfig.class);
        } catch (IOException e) {
            throw new IllegalStateException("读取 " + f.toAbsolutePath() + " 失败：" + e.getMessage(), e);
        }
    }

    /** 先写临时文件再替换，写到一半断电也不会留下坏文件 */
    public static synchronized void write(String dataDir, InstallConfig config) {
        Path f = file(dataDir);
        try {
            Files.createDirectories(f.getParent());
            Path tmp = f.resolveSibling(FILE + ".tmp");
            JSON.writeValue(tmp.toFile(), config);
            restrict(tmp);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new IllegalStateException("写入 " + f.toAbsolutePath() + " 失败：" + e.getMessage(), e);
        }
    }

    public static String encrypt(String dataDir, String masterKey, String plain) {
        if (plain == null || plain.isEmpty()) return plain;
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(dataDir, masterKey), "AES"), new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** 不是密文（比如手工改过的明文）就原样返回 */
    public static String decrypt(String dataDir, String masterKey, String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(dataDir, masterKey), "AES"), new GCMParameterSpec(128, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("install.json 里的密码解不开：主密钥和安装时用的不一致", e);
        }
    }

    private static byte[] key(String dataDir, String masterKey) throws IOException {
        if (masterKey != null && !masterKey.isBlank()) return Base64.getDecoder().decode(masterKey.trim());
        Path f = Path.of(dataDir, "master.key");
        if (Files.isRegularFile(f)) return Base64.getDecoder().decode(Files.readString(f).trim());
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        Files.createDirectories(f.getParent());
        Files.writeString(f, Base64.getEncoder().encodeToString(raw) + "\n");
        restrict(f);
        return raw;
    }

    private static void restrict(Path f) {
        try {
            Files.setPosixFilePermissions(f, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Windows 等不支持 POSIX 权限的文件系统
        }
    }
}
