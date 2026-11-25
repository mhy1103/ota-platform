package com.ota.platform.web.page;

import com.ota.platform.OtaPlatformApplication;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

/**
 * 这次启动的版本号：前端打包清单 + 打包时间算一个短哈希。换了 jar 就会变，
 * 开着的页面发现和自己加载时的不一样，就提示重新加载。
 */
@Component
public class BuildStamp {

    public static final String HEADER = "X-OTA-Build";

    private final String id;
    private final Instant time;

    public BuildStamp(ObjectProvider<BuildProperties> build) {
        StringBuilder seed = new StringBuilder();
        try (InputStream in = new ClassPathResource("static/vue/.vite/manifest.json").getInputStream()) {
            seed.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // 开发时没打包前端
        }
        BuildProperties b = build.getIfAvailable();
        if (b != null && b.getTime() != null) seed.append(b.getTime().toEpochMilli());
        if (seed.length() == 0) seed.append(System.currentTimeMillis());
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(seed.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            hash = Integer.toHexString(seed.toString().hashCode());
        }
        this.id = hash.substring(0, Math.min(12, hash.length()));
        this.time = b != null && b.getTime() != null ? b.getTime() : jarTime();
    }

    /** 没有打包信息时用 jar 文件的修改时间（就是换上去的时间），开发时用启动时间 */
    private static Instant jarTime() {
        try {
            File f = new ApplicationHome(OtaPlatformApplication.class).getSource();
            if (f != null && f.isFile()) return Instant.ofEpochMilli(f.lastModified());
        } catch (Exception ignored) {
            // 取不到就用启动时间
        }
        return Instant.now();
    }

    public Instant time() {
        return time;
    }

    public String id() {
        return id;
    }
}
