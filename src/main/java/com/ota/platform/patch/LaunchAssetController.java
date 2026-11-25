package com.ota.platform.patch;

import com.ota.platform.common.Hashes;
import com.ota.platform.ota.ReleaseStatsService;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.storage.BlobStore;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * 启动包下载地址。expo-updates 下载启动包时带 A-IM: bsdiff 和 Expo-Current-Update-ID（设备正在运行的版本）：
 * 有从那个版本到目标文件的补丁时返回 226 + 补丁，App 打补丁后按哈希校验，失败会自动改下完整文件；
 * 没有补丁时返回完整文件（对象存储跳转到 CDN）。
 */
@RestController
public class LaunchAssetController {

    private final PatchService patches;
    private final ReleaseService releases;
    private final BlobStore blobs;
    private final ReleaseStatsService stats;

    public LaunchAssetController(PatchService patches, ReleaseService releases, BlobStore blobs, ReleaseStatsService stats) {
        this.patches = patches;
        this.releases = releases;
        this.blobs = blobs;
        this.stats = stats;
    }

    /** 正在下载哪个版本（expo-updates 下载文件时带 Expo-Requested-Update-ID） */
    static String requestedRelease(HttpServletRequest req) {
        String id = req.getHeader("Expo-Requested-Update-ID");
        return id == null || id.isBlank() || id.length() > 36 ? null : id.trim().toLowerCase(Locale.ROOT);
    }

    @GetMapping("/l/{sha256}")
    public void launch(@PathVariable String sha256, HttpServletRequest req, HttpServletResponse res) throws IOException {
        if (!Hashes.isSha256Hex(sha256)) {
            res.sendError(404);
            return;
        }
        res.setHeader("Vary", "A-IM, Expo-Current-Update-ID");
        res.setHeader("Cache-Control", "private, max-age=0");
        Optional<Patch> patch = patchFor(sha256, req);
        if (patch.isPresent()) {
            Patch p = patch.get();
            res.setStatus(226);
            res.setHeader("IM", "bsdiff");
            res.setHeader("expo-base-update-id", req.getHeader("Expo-Current-Update-ID").trim().toLowerCase(Locale.ROOT));
            res.setContentType("application/octet-stream");
            res.setContentLengthLong(p.getPatchSize());
            copy(p.getPatchSha(), res);
            stats.download(requestedRelease(req), "patch", p.getPatchSize(), Math.max(0, p.getTargetSize() - p.getPatchSize()));
            return;
        }
        Path local = blobs.localPath(sha256);
        if (local == null) {
            if (!blobs.exists(sha256)) {
                res.sendError(404);
                return;
            }
            res.setStatus(302);
            res.setHeader("Location", blobs.publicUrl(sha256));
            stats.download(requestedRelease(req), "cdn", 0, 0);
            return;
        }
        if (!Files.isRegularFile(local)) {
            res.sendError(404);
            return;
        }
        long size = Files.size(local);
        res.setStatus(200);
        res.setContentType("application/javascript");
        res.setContentLengthLong(size);
        copy(sha256, res);
        stats.download(requestedRelease(req), "full", size, 0);
    }

    private Optional<Patch> patchFor(String targetSha, HttpServletRequest req) {
        String aim = req.getHeader("A-IM");
        String current = req.getHeader("Expo-Current-Update-ID");
        String platform = req.getHeader("Expo-Platform");
        if (aim == null || !aim.toLowerCase(Locale.ROOT).contains("bsdiff") || current == null || platform == null) return Optional.empty();
        Optional<Release> base = releases.find(current.trim().toLowerCase(Locale.ROOT));
        if (base.isEmpty() || base.get().isExternal()) return Optional.empty();
        return patches.launchSha(base.get().getId(), platform.trim().toLowerCase(Locale.ROOT))
                .filter(baseSha -> !baseSha.equals(targetSha))
                .flatMap(baseSha -> patches.ready(baseSha, targetSha));
    }

    private void copy(String sha, HttpServletResponse res) throws IOException {
        try (InputStream in = blobs.open(sha); OutputStream out = res.getOutputStream()) {
            in.transferTo(out);
        }
    }
}
