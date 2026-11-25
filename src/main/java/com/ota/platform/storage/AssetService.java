package com.ota.platform.storage;

import com.ota.platform.common.ApiException;
import com.ota.platform.common.Hashes;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.release.Asset;
import com.ota.platform.release.AssetRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class AssetService {

    private final AssetRepository assets;
    private final BlobStore blobs;
    private final OtaProperties props;
    private final Path tmpDir;

    public AssetService(AssetRepository assets, BlobStore blobs, OtaProperties props) throws IOException {
        this.assets = assets;
        this.blobs = blobs;
        this.props = props;
        this.tmpDir = Files.createDirectories(Path.of(props.getDataDir(), "tmp").toAbsolutePath());
    }

    public Path tmpDir() {
        return tmpDir;
    }

    /** 返回服务端还没有的文件哈希 */
    public List<String> missing(Collection<String> sha256s) {
        List<String> out = new ArrayList<>();
        for (String raw : new LinkedHashSet<>(sha256s)) {
            String sha = normalize(raw);
            if (!assets.existsById(sha) || !blobs.exists(sha)) out.add(sha);
        }
        return out;
    }

    /** 上传一个文件：边收边算哈希，和地址里的 SHA-256 一致才保存。已存在时直接返回。 */
    public Asset upload(String sha256, InputStream body) throws IOException {
        String sha = normalize(sha256);
        var existing = assets.findById(sha);
        if (existing.isPresent() && blobs.exists(sha)) {
            body.transferTo(OutputStream.nullOutputStream());
            return existing.get();
        }
        Path tmp = Files.createTempFile(tmpDir, "upload-", ".part");
        try {
            MessageDigest sha256d = Hashes.sha256();
            MessageDigest md5d = Hashes.md5();
            long size = 0;
            long max = props.getMaxAssetBytes();
            try (OutputStream out = Files.newOutputStream(tmp)) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = body.read(buf)) > 0) {
                    size += n;
                    if (size > max) throw ApiException.badRequest("ASSET_TOO_LARGE", "单个文件超过上限 " + max + " 字节");
                    sha256d.update(buf, 0, n);
                    md5d.update(buf, 0, n);
                    out.write(buf, 0, n);
                }
            }
            String actual = Hashes.hex(sha256d.digest());
            if (!actual.equals(sha)) {
                throw ApiException.badRequest("HASH_MISMATCH", "文件内容的 SHA-256 是 " + actual + "，和地址里的不一致");
            }
            blobs.put(sha, tmp);
            return record(sha, Hashes.hex(md5d.digest()), size);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    /** 把本机上的一个文件存进来（上传 ZIP 用），原文件不动 */
    public Asset store(Path file) throws IOException {
        MessageDigest sha256d = Hashes.sha256();
        MessageDigest md5d = Hashes.md5();
        long size = 0;
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                size += n;
                sha256d.update(buf, 0, n);
                md5d.update(buf, 0, n);
            }
        }
        if (size > props.getMaxAssetBytes()) {
            throw ApiException.badRequest("ASSET_TOO_LARGE", file.getFileName() + " 超过单个文件上限 " + props.getMaxAssetBytes() + " 字节");
        }
        String sha = Hashes.hex(sha256d.digest());
        var existing = assets.findById(sha);
        if (existing.isPresent() && blobs.exists(sha)) return existing.get();
        Path tmp = Files.createTempFile(tmpDir, "store-", ".part");
        try {
            Files.copy(file, tmp, StandardCopyOption.REPLACE_EXISTING);
            blobs.put(sha, tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
        return record(sha, Hashes.hex(md5d.digest()), size);
    }

    /** 只登记哈希、不存内容（外部链接版本：文件在 CDN 上） */
    public Asset register(String sha256, String md5, long size) {
        return record(normalize(sha256), md5, size);
    }

    private Asset record(String sha, String md5, long size) {
        var existing = assets.findById(sha);
        if (existing.isPresent()) return existing.get();
        try {
            return assets.save(new Asset(sha, md5, size));
        } catch (DataIntegrityViolationException race) {
            return assets.findById(sha).orElseThrow(() -> race);
        }
    }

    public static String normalize(String sha256) {
        String sha = sha256 == null ? "" : sha256.trim().toLowerCase(Locale.ROOT);
        if (!Hashes.isSha256Hex(sha)) throw ApiException.badRequest("BAD_SHA256", "不是有效的 SHA-256：" + sha256);
        return sha;
    }
}
