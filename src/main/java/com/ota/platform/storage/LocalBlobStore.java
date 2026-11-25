package com.ota.platform.storage;

import com.ota.platform.common.Hashes;
import com.ota.platform.config.OtaProperties;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** 存在本机磁盘：{data-dir}/blobs/ab/cd/abcd… */
public class LocalBlobStore implements BlobStore {

    private final Path root;
    private final OtaProperties props;

    public LocalBlobStore(OtaProperties props) throws IOException {
        this.props = props;
        this.root = Path.of(props.getDataDir(), "blobs").toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    @Override
    public boolean exists(String sha256) {
        Path p = localPath(sha256);
        return p != null && Files.isRegularFile(p);
    }

    @Override
    public void put(String sha256, Path verifiedFile) throws IOException {
        Path target = localPath(sha256);
        if (target == null) throw new IllegalArgumentException("bad sha256");
        Files.createDirectories(target.getParent());
        try {
            Files.move(verifiedFile, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(verifiedFile, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @Override
    public InputStream open(String sha256) throws IOException {
        Path p = localPath(sha256);
        if (p == null || !Files.isRegularFile(p)) throw new NoSuchFileException(sha256);
        return Files.newInputStream(p);
    }

    @Override
    public void delete(String sha256) throws IOException {
        Path p = localPath(sha256);
        if (p != null) Files.deleteIfExists(p);
    }

    @Override
    public Path localPath(String sha256) {
        if (!Hashes.isSha256Hex(sha256)) return null;
        return root.resolve(sha256.substring(0, 2)).resolve(sha256.substring(2, 4)).resolve(sha256);
    }

    @Override
    public String publicUrl(String sha256) {
        return props.baseUrl() + "/a/" + sha256;
    }

    @Override
    public String describe() {
        return "本机磁盘 " + root;
    }
}
