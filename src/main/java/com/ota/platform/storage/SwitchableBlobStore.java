package com.ota.platform.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;

/** 当前用的文件存储。在系统配置里切换存储时，文件复制完就换成新的，不用重启 */
public class SwitchableBlobStore implements BlobStore {

    private volatile BlobStore delegate;

    public SwitchableBlobStore(BlobStore initial) {
        this.delegate = initial;
    }

    public BlobStore current() {
        return delegate;
    }

    void swap(BlobStore next) {
        this.delegate = next;
    }

    @Override
    public boolean exists(String sha256) {
        return delegate.exists(sha256);
    }

    @Override
    public void put(String sha256, Path verifiedFile) throws IOException {
        delegate.put(sha256, verifiedFile);
    }

    @Override
    public InputStream open(String sha256) throws IOException {
        return delegate.open(sha256);
    }

    @Override
    public void delete(String sha256) throws IOException {
        delegate.delete(sha256);
    }

    @Override
    public Path localPath(String sha256) {
        return delegate.localPath(sha256);
    }

    @Override
    public String publicUrl(String sha256) {
        return delegate.publicUrl(sha256);
    }

    @Override
    public String describe() {
        return delegate.describe();
    }
}
