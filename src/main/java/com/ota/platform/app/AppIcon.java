package com.ota.platform.app;

import jakarta.persistence.*;
import java.time.Instant;

/** App 图标（PNG / JPEG / WebP），单独一张表，列 App 时不用把图片读出来 */
@Entity
@Table(name = "ota_app_icon")
public class AppIcon {

    @Id
    @Column(name = "app_id")
    private Long appId;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Lob
    @Column(nullable = false, length = 1_048_576)
    private byte[] data;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected AppIcon() {}

    public AppIcon(Long appId, String contentType, byte[] data) {
        this.appId = appId;
        this.contentType = contentType;
        this.data = data;
    }

    public Long getAppId() { return appId; }
    public String getContentType() { return contentType; }
    public byte[] getData() { return data; }
    public Instant getUpdatedAt() { return updatedAt; }
}
