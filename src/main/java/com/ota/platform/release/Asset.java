package com.ota.platform.release;

import jakarta.persistence.*;

import java.time.Instant;

/** 按内容存储的文件。同一个文件只存一份，所有版本、所有 App 共用。 */
@Entity
@Table(name = "ota_asset")
public class Asset {

    /** 文件内容的 SHA-256，十六进制小写 */
    @Id
    @Column(length = 64)
    private String sha256;

    /** 文件内容的 MD5，十六进制小写，清单里的 key 字段 */
    @Column(nullable = false, length = 32)
    private String md5;

    @Column(nullable = false)
    private long size;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Asset() {}

    public Asset(String sha256, String md5, long size) {
        this.sha256 = sha256;
        this.md5 = md5;
        this.size = size;
    }

    public String getSha256() { return sha256; }
    public String getMd5() { return md5; }
    public long getSize() { return size; }
    public Instant getCreatedAt() { return createdAt; }
}
