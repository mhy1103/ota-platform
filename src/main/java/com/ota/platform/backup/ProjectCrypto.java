package com.ota.platform.backup;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * 项目备份文件的加密：边写边加密，几个 G 的备份也不占内存。
 * 格式：OTAPROJ1 | 迭代次数(4) | 盐(16) | IV(16) | AES-256-CTR 密文 | HMAC-SHA256(前面全部)(32)。
 * 密钥用 PBKDF2-HMAC-SHA256 从备份密码派生（前 32 字节加密、后 32 字节校验）；先校验再解密，密码不对或文件被改过都解不开。
 */
public final class ProjectCrypto {

    static final byte[] MAGIC = "OTAPROJ1".getBytes(StandardCharsets.US_ASCII);
    static final int ITERATIONS = 210_000;
    static final int HEADER = MAGIC.length + 4 + 16 + 16;
    static final int TAG = 32;

    private ProjectCrypto() {}

    public static class BadPassword extends IOException {
        BadPassword() { super("备份密码不对，或者文件不完整、被改过"); }
    }

    /** 包一层加密输出流；关掉它时写入校验码并关掉下面的流 */
    public static OutputStream encrypt(OutputStream raw, String password) throws IOException {
        try {
            SecureRandom rnd = new SecureRandom();
            byte[] salt = new byte[16], iv = new byte[16];
            rnd.nextBytes(salt);
            rnd.nextBytes(iv);
            byte[][] keys = derive(password, salt, ITERATIONS);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keys[1], "HmacSHA256"));
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys[0], "AES"), new IvParameterSpec(iv));
            byte[] header = ByteBuffer.allocate(HEADER).put(MAGIC).putInt(ITERATIONS).put(salt).put(iv).array();
            raw.write(header);
            mac.update(header);
            return new OutputStream() {
                private boolean closed;

                @Override
                public void write(int b) throws IOException {
                    write(new byte[]{(byte) b}, 0, 1);
                }

                @Override
                public void write(byte[] b, int off, int len) throws IOException {
                    byte[] out = c.update(b, off, len);
                    if (out != null && out.length > 0) {
                        mac.update(out);
                        raw.write(out);
                    }
                }

                @Override
                public void flush() throws IOException {
                    raw.flush();
                }

                @Override
                public void close() throws IOException {
                    if (closed) return;
                    closed = true;
                    try {
                        byte[] fin = c.doFinal();
                        if (fin.length > 0) {
                            mac.update(fin);
                            raw.write(fin);
                        }
                        raw.write(mac.doFinal());
                    } catch (GeneralSecurityException e) {
                        throw new IOException(e);
                    } finally {
                        raw.close();
                    }
                }
            };
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    /** 是不是项目备份文件（看开头） */
    public static boolean looksEncrypted(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] head = in.readNBytes(MAGIC.length);
            return Arrays.equals(head, MAGIC);
        } catch (IOException e) {
            return false;
        }
    }

    /** 先完整校验，再解密到 out */
    public static void decrypt(Path in, String password, Path out) throws IOException {
        long size = Files.size(in);
        if (size < HEADER + TAG) throw new BadPassword();
        byte[] header;
        try (InputStream is = new BufferedInputStream(Files.newInputStream(in))) {
            header = is.readNBytes(HEADER);
        }
        ByteBuffer h = ByteBuffer.wrap(header);
        byte[] magic = new byte[MAGIC.length];
        h.get(magic);
        if (!Arrays.equals(magic, MAGIC)) throw new IOException("不是项目备份文件");
        int iterations = h.getInt();
        if (iterations < 10_000 || iterations > 5_000_000) throw new BadPassword();
        byte[] salt = new byte[16], iv = new byte[16];
        h.get(salt);
        h.get(iv);
        try {
            byte[][] keys = derive(password, salt, iterations);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(keys[1], "HmacSHA256"));
            long body = size - HEADER - TAG;
            byte[] tag;
            try (InputStream is = new BufferedInputStream(Files.newInputStream(in), 1 << 16)) {
                mac.update(is.readNBytes(HEADER));
                copy(is, body, (buf, n) -> mac.update(buf, 0, n));
                tag = is.readNBytes(TAG);
            }
            if (!MessageDigest.isEqual(tag, mac.doFinal())) throw new BadPassword();
            Cipher c = Cipher.getInstance("AES/CTR/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keys[0], "AES"), new IvParameterSpec(iv));
            try (InputStream is = new BufferedInputStream(Files.newInputStream(in), 1 << 16);
                 OutputStream os = new BufferedOutputStream(Files.newOutputStream(out), 1 << 16)) {
                is.skipNBytes(HEADER);
                copy(is, body, (buf, n) -> {
                    byte[] plain = c.update(buf, 0, n);
                    if (plain != null) os.write(plain);
                });
                os.write(c.doFinal());
            }
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private interface Chunk {
        void accept(byte[] buf, int n) throws IOException;
    }

    private static void copy(InputStream is, long count, Chunk sink) throws IOException {
        byte[] buf = new byte[1 << 16];
        long left = count;
        while (left > 0) {
            int n = is.read(buf, 0, (int) Math.min(buf.length, left));
            if (n < 0) throw new BadPassword();
            sink.accept(buf, n);
            left -= n;
        }
    }

    private static byte[][] derive(String password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, 512);
        byte[] k = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        spec.clearPassword();
        return new byte[][]{Arrays.copyOfRange(k, 0, 32), Arrays.copyOfRange(k, 32, 64)};
    }
}
