package com.ota.platform.release;

import jakarta.persistence.*;

/** 某个版本在某个平台上包含的一个文件：启动包或资源。 */
@Entity
@Table(name = "ota_release_file", indexes = {
        @Index(name = "idx_release_file_release", columnList = "release_id,platform"),
        @Index(name = "idx_release_file_asset", columnList = "asset_sha256")})
public class ReleaseFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "release_id", nullable = false, length = 36)
    private String releaseId;

    @Column(nullable = false, length = 16)
    private String platform;

    /** 文件在导出的 dist 目录里的相对路径 */
    @Column(nullable = false, length = 500)
    private String path;

    @Column(name = "asset_sha256", nullable = false, length = 64)
    private String assetSha256;

    /** 资源扩展名（不带点），启动包为空 */
    @Column(length = 16)
    private String ext;

    @Column(nullable = false)
    private boolean launch;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected ReleaseFile() {}

    public ReleaseFile(String releaseId, String platform, String path, String assetSha256, String ext, boolean launch, int sortOrder) {
        this.releaseId = releaseId;
        this.platform = platform;
        this.path = path;
        this.assetSha256 = assetSha256;
        this.ext = ext;
        this.launch = launch;
        this.sortOrder = sortOrder;
    }

    public Long getId() { return id; }
    public String getReleaseId() { return releaseId; }
    public String getPlatform() { return platform; }
    public String getPath() { return path; }
    public String getAssetSha256() { return assetSha256; }
    public String getExt() { return ext; }
    public boolean isLaunch() { return launch; }
    public int getSortOrder() { return sortOrder; }
}
