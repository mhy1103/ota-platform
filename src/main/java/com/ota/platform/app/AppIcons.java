package com.ota.platform.app;

import com.ota.platform.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.UUID;

/** App 图标：上传、删除、读取。只收 PNG / JPEG / WebP，最大 MAX_BYTES，边长 32–4096 像素 */
@Service
public class AppIcons {

    public static final int MAX_BYTES = 512 * 1024;

    private final AppIconRepo icons;
    private final OtaAppRepository apps;

    public AppIcons(AppIconRepo icons, OtaAppRepository apps) {
        this.icons = icons;
        this.apps = apps;
    }

    @Transactional
    public OtaApp set(OtaApp app, byte[] data) {
        if (data == null || data.length == 0) throw ApiException.badRequest("ICON_EMPTY", "请选择图标图片");
        if (data.length > MAX_BYTES) throw ApiException.badRequest("ICON_TOO_LARGE", "图标最大 512 KB，压缩一下再传");
        String type = type(data);
        if (type == null) throw ApiException.badRequest("ICON_TYPE", "图标只支持 PNG、JPG、WebP");
        if (!"image/webp".equals(type)) {
            BufferedImage img;
            try {
                img = ImageIO.read(new ByteArrayInputStream(data));
            } catch (Exception e) {
                img = null;
            }
            if (img == null) throw ApiException.badRequest("ICON_BROKEN", "图片打不开，换一张试试");
            if (img.getWidth() < 32 || img.getHeight() < 32) throw ApiException.badRequest("ICON_SMALL", "图标至少 32×32 像素，建议 512×512");
            if (img.getWidth() > 4096 || img.getHeight() > 4096) throw ApiException.badRequest("ICON_LARGE", "图标边长最多 4096 像素");
        }
        icons.save(new AppIcon(app.getId(), type, data));
        app.setIconVersion(UUID.randomUUID().toString().replace("-", "").substring(0, 10));
        return apps.save(app);
    }

    @Transactional
    public OtaApp remove(OtaApp app) {
        if (icons.existsById(app.getId())) icons.deleteById(app.getId());
        app.setIconVersion(null);
        return apps.save(app);
    }

    public Optional<AppIcon> get(Long appId) {
        return icons.findById(appId);
    }

    /** 删除 App 时一起删 */
    @Transactional
    public void purge(Long appId) {
        if (icons.existsById(appId)) icons.deleteById(appId);
    }

    static String type(byte[] d) {
        if (d.length > 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') return "image/png";
        if (d.length > 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (d.length > 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') return "image/webp";
        return null;
    }
}
