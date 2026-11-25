package com.ota.platform.storage;

import com.ota.platform.patch.Patch;
import com.ota.platform.patch.PatchRepository;
import com.ota.platform.release.Asset;
import com.ota.platform.release.AssetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 存储用量和清理：删版本、删 App 后，没有任何版本再用的文件可以清掉。 */
@Service
public class StorageService {

    private static final Logger log = LoggerFactory.getLogger(StorageService.class);

    private final AssetRepository assets;
    private final PatchRepository patches;
    private final BlobStore blobs;

    public StorageService(AssetRepository assets, PatchRepository patches, BlobStore blobs) {
        this.assets = assets;
        this.patches = patches;
        this.blobs = blobs;
    }

    public record Usage(long files, long bytes, long patches, long patchBytes) {
        public long totalBytes() {
            return bytes + patchBytes;
        }
    }

    public record Cleaned(int files, long bytes, int patches) {}

    public Usage usage() {
        Object[] f = assets.storedUsage().get(0);
        Object[] p = patches.readyUsage().get(0);
        return new Usage(((Number) f[0]).longValue(), ((Number) f[1]).longValue(), ((Number) p[0]).longValue(), ((Number) p[1]).longValue());
    }

    /** 清理：先删用不上的补丁，再删没有引用的文件。minAge 之内登记的文件不动（可能正在上传、还没登记版本） */
    public Cleaned clean(Duration minAge) {
        int patchCount = 0;
        for (Patch p : patches.findOrphans()) {
            patches.delete(p);
            patchCount++;
        }
        int files = 0;
        long bytes = 0;
        List<Asset> unused = assets.findUnreferenced(Instant.now().minus(minAge));
        for (Asset a : unused) {
            try {
                blobs.delete(a.getSha256());
                assets.delete(a);
                files++;
                bytes += a.getSize();
            } catch (Exception e) {
                log.warn("清理文件 {} 失败：{}", a.getSha256(), e.toString());
            }
        }
        return new Cleaned(files, bytes, patchCount);
    }
}
