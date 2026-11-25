package com.ota.platform.patch;

import jakarta.persistence.*;

import java.time.Instant;

/** 启动包的 bsdiff 补丁：从 baseSha 的文件打补丁得到 targetSha 的文件。补丁本身按内容存在文件存储里。 */
@Entity
@Table(name = "ota_patch", uniqueConstraints = @UniqueConstraint(columnNames = {"base_sha", "target_sha"}))
public class Patch {

    public static final String READY = "READY";
    /** 补丁不比完整文件小多少，不用 */
    public static final String USELESS = "USELESS";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "base_sha", nullable = false, length = 64)
    private String baseSha;

    @Column(name = "target_sha", nullable = false, length = 64)
    private String targetSha;

    @Column(name = "patch_sha", length = 64)
    private String patchSha;

    @Column(name = "patch_size")
    private long patchSize;

    @Column(name = "target_size")
    private long targetSize;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(length = 300)
    private String error;

    @Column(name = "took_ms")
    private long tookMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Patch() {}

    public Patch(String baseSha, String targetSha) {
        this.baseSha = baseSha;
        this.targetSha = targetSha;
    }

    public Long getId() { return id; }
    public String getBaseSha() { return baseSha; }
    public String getTargetSha() { return targetSha; }
    public String getPatchSha() { return patchSha; }
    public void setPatchSha(String patchSha) { this.patchSha = patchSha; }
    public long getPatchSize() { return patchSize; }
    public void setPatchSize(long patchSize) { this.patchSize = patchSize; }
    public long getTargetSize() { return targetSize; }
    public void setTargetSize(long targetSize) { this.targetSize = targetSize; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getError() { return error; }
    public void setError(String error) { this.error = error == null || error.length() <= 300 ? error : error.substring(0, 300); }
    public long getTookMs() { return tookMs; }
    public void setTookMs(long tookMs) { this.tookMs = tookMs; }
    public Instant getCreatedAt() { return createdAt; }
}
