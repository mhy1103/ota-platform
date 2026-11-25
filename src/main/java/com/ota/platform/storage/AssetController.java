package com.ota.platform.storage;

import com.ota.platform.common.Hashes;
import com.ota.platform.ota.ReleaseStatsService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** 文件下载。本机存储直接返回文件；对象存储跳转到 CDN 地址。地址按内容寻址，内容永远不变，可以长期缓存。 */
@RestController
public class AssetController {

    private final BlobStore blobs;
    private final ReleaseStatsService stats;

    public AssetController(BlobStore blobs, ReleaseStatsService stats) {
        this.blobs = blobs;
        this.stats = stats;
    }

    @GetMapping("/a/{sha256}")
    public ResponseEntity<Resource> get(@PathVariable String sha256, HttpServletRequest req) throws IOException {
        if (!Hashes.isSha256Hex(sha256)) return ResponseEntity.notFound().build();
        Path file = blobs.localPath(sha256);
        if (file == null) {
            if (!blobs.exists(sha256)) return ResponseEntity.notFound().build();
            return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, blobs.publicUrl(sha256)).build();
        }
        if (!Files.isRegularFile(file)) return ResponseEntity.notFound().build();
        long size = Files.size(file);
        String release = req.getHeader("Expo-Requested-Update-ID");
        if (release != null && !release.isBlank() && release.length() <= 36) {
            stats.download(release.trim().toLowerCase(Locale.ROOT), "asset", size, 0);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(size)
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .eTag("\"" + sha256 + "\"")
                .body(new FileSystemResource(file));
    }
}
