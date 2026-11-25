package com.ota.platform.pkg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.platform.app.OtaApp;
import com.ota.platform.release.Asset;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.storage.AssetService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** 后台上传的 ZIP（npx expo export 导出的 dist 目录压缩包）：安全解压，逐个文件按哈希入库，登记成一个版本。 */
@Service
public class PackageImporter {

    private final AssetService assets;
    private final ReleaseService releases;
    private final ObjectMapper mapper;

    public PackageImporter(AssetService assets, ReleaseService releases, ObjectMapper mapper) {
        this.assets = assets;
        this.releases = releases;
        this.mapper = mapper;
    }

    /** runtime 为空时从包里识别 */
    public Release importZip(OtaApp app, MultipartFile file, String message, boolean forceUpdate, String runtime, String by)
            throws IOException {
        if (file == null || file.isEmpty()) throw new PackageFiles.PackageException("文件为空");
        Path work = Files.createTempDirectory(assets.tmpDir(), "zip-");
        try {
            Path zip = work.resolve("upload.zip");
            file.transferTo(zip);
            return importZip(app, zip, message, forceUpdate, runtime, by);
        } finally {
            PackageFiles.deleteDirQuietly(work);
        }
    }

    public Release importZip(OtaApp app, Path zip, String message, boolean forceUpdate, String runtime, String by) throws IOException {
        if (!PackageFiles.isZip(zip)) {
            throw new PackageFiles.PackageException("不是 ZIP 压缩包" + PackageFiles.guessKind(zip)
                    + "。需要 npx expo export 导出的整个 dist 目录压缩包（metadata.json + 启动包 + 资源文件）");
        }
        Path dir = Files.createTempDirectory(assets.tmpDir(), "unzip-");
        try {
            PackageFiles.unzip(zip, dir);
            return importDir(app, PackageFiles.packageRoot(dir), message, forceUpdate, by, Release.SOURCE_UPLOAD, runtime);
        } finally {
            PackageFiles.deleteDirQuietly(dir);
        }
    }

    /** 导入一个解压好的 dist 目录。runtime 为空时从 expoConfig.json 识别，识别不到按 1.0.0 */
    public Release importDir(OtaApp app, Path root, String message, boolean forceUpdate, String by, String source, String runtime)
            throws IOException {
        Path meta = root.resolve("metadata.json");
        if (!Files.isRegularFile(meta)) throw new PackageFiles.PackageException("压缩包里没有 metadata.json，不是 npx expo export 导出的 dist 目录");
        JsonNode metadata;
        try {
            metadata = mapper.readTree(meta.toFile());
        } catch (IOException e) {
            throw new PackageFiles.PackageException("metadata.json 无法解析：" + e.getMessage());
        }
        Path cfg = root.resolve("expoConfig.json");
        JsonNode expoConfig = null;
        if (Files.isRegularFile(cfg)) {
            try {
                expoConfig = mapper.readTree(cfg.toFile());
            } catch (IOException e) {
                throw new PackageFiles.PackageException("expoConfig.json 无法解析：" + e.getMessage());
            }
        }
        String rv = runtime != null && !runtime.isBlank() ? runtime
                : Optional.ofNullable(PackageFiles.runtimeOf(root)).orElse("1.0.0");

        Map<String, String> hashes = new LinkedHashMap<>();
        Path base = root.toAbsolutePath().normalize();
        for (ReleaseService.MetaFile f : releases.parseMetadata(metadata)) {
            if (hashes.containsKey(f.path())) continue;
            Path file = base.resolve(f.path()).normalize();
            if (!file.startsWith(base) || !Files.isRegularFile(file)) {
                throw new PackageFiles.PackageException("缺少文件 " + f.path() + "（metadata.json 里列出了，压缩包里没有）");
            }
            Asset a = assets.store(file);
            hashes.put(f.path(), a.getSha256());
        }
        return releases.create(app, rv, message, forceUpdate, metadata, expoConfig, hashes, by, source);
    }
}
