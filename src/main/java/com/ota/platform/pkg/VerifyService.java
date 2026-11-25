package com.ota.platform.pkg;

import com.ota.platform.installer.InstallerPackage;
import com.ota.platform.installer.InstallerService;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.common.Hashes;
import com.ota.platform.patch.Patch;
import com.ota.platform.patch.PatchService;
import com.ota.platform.release.*;
import com.ota.platform.signing.CodeSigning;
import com.ota.platform.signing.PemSupport;
import com.ota.platform.storage.BlobStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 校验一个版本能不能被 App 正常下载和验签：文件是否齐全、哈希是否一致、Runtime 是否对得上、签名是否配对。
 * 存在服务器上的版本逐个核对文件；外部链接版本从链接逐个下载，核对添加时记录的哈希。
 */
@Service
public class VerifyService {

    private final AppService apps;
    private final ReleaseService releases;
    private final AssetRepository assets;
    private final BlobStore blobs;
    private final ExternalPackage externalPackage;
    private final PatchService patches;
    private final ObjectMapper mapper;
    private final InstallerService installers;

    public VerifyService(AppService apps, ReleaseService releases, AssetRepository assets, BlobStore blobs,
                         ExternalPackage externalPackage, PatchService patches, ObjectMapper mapper, InstallerService installers) {
        this.installers = installers;
        this.apps = apps;
        this.releases = releases;
        this.assets = assets;
        this.blobs = blobs;
        this.externalPackage = externalPackage;
        this.patches = patches;
        this.mapper = mapper;
    }

    public Result verify(OtaApp app, Release r) {
        Result res = new Result(r);
        List<ReleaseFile> files = releases.allFiles(r.getId());
        if (files.isEmpty()) {
            res.add("文件清单", "error", "这个版本没有任何文件记录");
            signatureCheck(app, r, res);
            return res.finish();
        }
        Map<String, Asset> byHash = assets.findAllById(files.stream().map(ReleaseFile::getAssetSha256).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Asset::getSha256, Function.identity()));
        Map<String, List<ReleaseFile>> byPlatform = new TreeMap<>();
        files.forEach(f -> byPlatform.computeIfAbsent(f.getPlatform(), k -> new ArrayList<>()).add(f));
        res.add("包结构", "ok", "平台：" + String.join("、", byPlatform.keySet()) + " · 共 " + files.size() + " 个文件 · " + PackageFiles.size(r.getTotalBytes()));

        if (r.isExternal()) verifyExternal(r, files, byHash, res);
        else verifyStored(files, byHash, res);

        runtimeCheck(r, res);
        signatureCheck(app, r, res);
        if (!r.isExternal()) patchInfo(app, files, res);
        installerCheck(app, r, res);
        return res.finish();
    }

    /** 上传了同一 Runtime 的安装包时，一起检查安装包能不能收到这个更新（地址、渠道、签名） */
    private void installerCheck(OtaApp app, Release r, Result res) {
        List<InstallerPackage> pkgs = installers.forRelease(app, r);
        if (pkgs.isEmpty()) {
            res.add("安装包", "skip", "没有上传 Runtime " + r.getRuntimeVersion() + " 的安装包（可选）。在版本发布页上传 APK / IPA 后，校验时会一起检查安装包能不能收到这个更新");
            return;
        }
        for (InstallerPackage p : pkgs) {
            List<InstallerService.Check> checks = installers.checks(app, p);
            String level = InstallerService.level(checks);
            List<String> bad = checks.stream().filter(c -> !"ok".equals(c.state())).map(c -> c.label() + "：" + c.text()).toList();
            res.add("安装包 · " + InstallerService.title(p), level,
                    bad.isEmpty() ? "能收到这个更新：更新地址、运行版本、渠道、签名、更新新旧都对得上" : String.join("；", bad));
        }
    }

    // ==================== 服务器上的文件 ====================

    private void verifyStored(List<ReleaseFile> files, Map<String, Asset> byHash, Result res) {
        boolean local = !files.isEmpty() && blobs.localPath(files.get(0).getAssetSha256()) != null;
        List<String> bundleProblems = new ArrayList<>();
        List<String> assetProblems = new ArrayList<>();
        long bundleBytes = 0;
        int assetCount = 0;
        Set<String> checked = new HashSet<>();
        for (ReleaseFile f : files) {
            if (!f.isLaunch()) assetCount++;
            if (!checked.add(f.getAssetSha256() + (f.isLaunch() ? "L" : ""))) continue;
            String problem = storedProblem(f, byHash.get(f.getAssetSha256()), local || f.isLaunch());
            if (f.isLaunch()) {
                if (problem != null) bundleProblems.add(f.getPlatform() + " 的启动包" + problem);
                else bundleBytes += byHash.get(f.getAssetSha256()).getSize();
            } else if (problem != null) {
                assetProblems.add(f.getPath() + " " + problem);
            }
        }
        long bundles = files.stream().filter(ReleaseFile::isLaunch).count();
        res.add("启动包", bundleProblems.isEmpty() ? "ok" : "error", bundleProblems.isEmpty()
                ? bundles + " 个平台的启动包完整，内容和哈希一致 · 共 " + PackageFiles.size(bundleBytes)
                : String.join("；", bundleProblems));
        if (assetCount == 0) {
            res.add("资源文件", "ok", "没有额外的图片、字体等资源");
        } else if (assetProblems.isEmpty()) {
            res.add("资源文件", "ok", "全部 " + assetCount + " 个资源文件都在" + (local ? "，内容和哈希一致" : "（对象存储里存在）"));
        } else {
            res.add("资源文件", "error", "有 " + assetProblems.size() + " 个文件有问题："
                    + String.join("；", assetProblems.subList(0, Math.min(3, assetProblems.size()))) + (assetProblems.size() > 3 ? " 等" : ""));
        }
        res.add("下发", "ok", local ? "App 从本服务器下载文件（" + blobs.describe() + "）" : "App 从对象存储 / CDN 下载文件（" + blobs.describe() + "）");
    }

    private String storedProblem(ReleaseFile f, Asset asset, boolean rehash) {
        if (asset == null) return "没有文件记录";
        try {
            if (!blobs.exists(f.getAssetSha256())) return "在存储里找不到";
            if (!rehash) return null;
            MessageDigest md = Hashes.sha256();
            long size = 0;
            try (InputStream in = blobs.open(f.getAssetSha256())) {
                byte[] buf = new byte[64 * 1024];
                int n;
                while ((n = in.read(buf)) > 0) {
                    md.update(buf, 0, n);
                    size += n;
                }
            }
            if (!Hashes.hex(md.digest()).equals(f.getAssetSha256())) return "内容和哈希不一致（文件被改动过）";
            if (size != asset.getSize()) return "大小和记录不一致";
            return null;
        } catch (Exception e) {
            return "读取失败：" + e.getMessage();
        }
    }

    // ==================== 外部链接 ====================

    private void verifyExternal(Release r, List<ReleaseFile> files, Map<String, Asset> byHash, Result res) {
        URI base = URI.create(r.getExternalBaseUrl());
        boolean https = "https".equalsIgnoreCase(base.getScheme());
        res.add("链接", https ? "ok" : "warn", shortUrl(r.getExternalBaseUrl())
                + (https ? "" : " · 不是 https：iOS 和 Android 默认会拦截明文 http 下载，建议换成 https"));
        try {
            JsonNode remote = mapper.readTree(externalPackage.metadata(base));
            boolean same = remote.equals(mapper.readTree(r.getMetadataJson()));
            res.add("metadata.json", same ? "ok" : "error", same ? "和添加时记录的一致"
                    : "外部链接上的 metadata.json 和添加时不一样，目录可能被换成了别的版本。App 用的是添加时记录的哈希，下载会校验失败。请重新添加这个版本");
        } catch (IOException e) {
            res.add("metadata.json", "error", e.getMessage());
        }
        Map<String, ReleaseFile> byPath = new LinkedHashMap<>();
        files.forEach(f -> byPath.putIfAbsent(f.getPath(), f));
        List<ExternalPackage.Probe> probes;
        try {
            probes = externalPackage.probe(base, byPath.keySet());
        } catch (IOException e) {
            res.add("启动包", "error", e.getMessage());
            res.skip("资源文件");
            return;
        }
        List<String> bundleProblems = new ArrayList<>();
        List<String> assetProblems = new ArrayList<>();
        long bundleBytes = 0;
        for (ExternalPackage.Probe p : probes) {
            ReleaseFile f = byPath.get(p.path());
            String problem = p.error() != null ? "下载失败（" + p.error() + "）"
                    : !Hashes.hexOfBase64Url(p.digest().hash()).equals(f.getAssetSha256()) ? "内容和添加时不一样（文件被替换过）" : null;
            if (f.isLaunch()) {
                if (problem != null) bundleProblems.add(f.getPlatform() + " 的启动包" + problem);
                else bundleBytes += p.digest().size();
            } else if (problem != null) {
                assetProblems.add(p.path() + " " + problem);
            }
        }
        long assetCount = byPath.values().stream().filter(f -> !f.isLaunch()).count();
        res.add("启动包", bundleProblems.isEmpty() ? "ok" : "error", bundleProblems.isEmpty()
                ? "启动包都能下载，和记录的哈希一致 · 共 " + PackageFiles.size(bundleBytes) : String.join("；", bundleProblems));
        if (assetCount == 0) res.add("资源文件", "ok", "没有额外的图片、字体等资源");
        else if (assetProblems.isEmpty()) res.add("资源文件", "ok", "全部 " + assetCount + " 个资源文件都能下载，和记录的哈希一致");
        else res.add("资源文件", "error", "有 " + assetProblems.size() + " 个文件有问题："
                    + String.join("；", assetProblems.subList(0, Math.min(3, assetProblems.size()))) + (assetProblems.size() > 3 ? " 等" : "")
                    + "。App 下载到有问题的文件会校验失败，整个更新都不会生效");
        res.add("下发", "ok", "App 直接从外部链接下载这 " + byPath.size() + " 个文件（共 " + PackageFiles.size(r.getTotalBytes())
                + "），不经过本服务器；服务器只返回签名后的清单");
    }

    // ==================== Runtime、签名、补丁 ====================

    private void runtimeCheck(Release r, Result res) {
        if (r.getExpoConfigJson() == null) {
            res.add("Runtime 版本", "warn", "没有 expoConfig.json，按 Runtime " + r.getRuntimeVersion() + " 下发");
            return;
        }
        try {
            JsonNode cfg = mapper.readTree(r.getExpoConfigJson());
            String declared = cfg.path("runtimeVersion").isTextual() ? cfg.path("runtimeVersion").asText()
                    : cfg.path("version").isTextual() ? cfg.path("version").asText() : null;
            if (declared == null) res.add("Runtime 版本", "warn", "expoConfig.json 里没有 runtimeVersion，按 Runtime " + r.getRuntimeVersion() + " 下发");
            else if (!declared.equals(r.getRuntimeVersion())) res.add("Runtime 版本", "warn", "包里的 Runtime 是 " + declared
                    + "，这个版本登记在 Runtime " + r.getRuntimeVersion() + " 下。只有 Runtime 是 " + r.getRuntimeVersion() + " 的安装包会收到它");
            else res.add("Runtime 版本", "ok", "Runtime " + declared + "，与版本记录一致");
        } catch (IOException e) {
            res.add("Runtime 版本", "warn", "expoConfig.json 无法解析");
        }
    }

    private void signatureCheck(OtaApp app, Release r, Result res) {
        try {
            X509Certificate cert = PemSupport.parseCertificate(app.getCertificatePem());
            byte[] data = r.getMetadataJson().getBytes(StandardCharsets.UTF_8);
            String sig = CodeSigning.sign(apps.privateKey(app), data);
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(cert.getPublicKey());
            verifier.update(data);
            boolean ok = verifier.verify(Base64.getDecoder().decode(sig));
            boolean valid = cert.getNotAfter().after(new Date());
            if (!ok) res.add("签名", "error", "私钥和证书不配对，App 会拒绝这个更新。去「签名配置」检查");
            else if (!valid) res.add("签名", "error", "证书已经过期，App 会拒绝更新");
            else res.add("签名", "ok", "用 App 私钥签名、证书验签通过，keyid「" + app.getKeyId() + "」");
        } catch (Exception e) {
            res.add("签名", "error", "签名检查失败：" + e.getMessage());
        }
    }

    private void patchInfo(OtaApp app, List<ReleaseFile> files, Result res) {
        if (!app.isPatchEnabled()) return;
        Set<String> launch = files.stream().filter(ReleaseFile::isLaunch).map(ReleaseFile::getAssetSha256).collect(Collectors.toSet());
        List<Patch> ps = patches.forTargets(launch);
        long ready = ps.stream().filter(p -> Patch.READY.equals(p.getStatus())).count();
        if (ps.isEmpty()) {
            res.add("增量更新", "skip", "还没有补丁：发布到渠道后，会为同一 Runtime 的最近几个版本生成启动包补丁");
            return;
        }
        long saved = ps.stream().filter(p -> Patch.READY.equals(p.getStatus())).mapToLong(p -> p.getTargetSize() - p.getPatchSize()).max().orElse(0);
        res.add("增量更新", "ok", ready + " 个补丁可用" + (ready > 0 ? "，最多省 " + PackageFiles.size(saved) : "")
                + (ps.size() > ready ? "；" + (ps.size() - ready) + " 个改动太大，直接下完整文件" : ""));
    }

    private static String shortUrl(String url) {
        return url.length() > 80 ? url.substring(0, 77) + "…" : url;
    }

    // ==================== 结果 ====================

    public static class Result {
        private final Release release;
        private final List<Map<String, String>> checks = new ArrayList<>();
        private String level = "ok";
        private String headline = "校验通过";

        Result(Release release) {
            this.release = release;
        }

        void add(String label, String state, String text) {
            Map<String, String> c = new LinkedHashMap<>();
            c.put("label", label);
            c.put("state", state);
            c.put("text", text);
            checks.add(c);
        }

        void skip(String... labels) {
            for (String l : labels) add(l, "skip", "前面的检查没通过，跳过");
        }

        Result finish() {
            boolean error = checks.stream().anyMatch(c -> "error".equals(c.get("state")));
            boolean warn = checks.stream().anyMatch(c -> "warn".equals(c.get("state")));
            level = error ? "error" : warn ? "warn" : "ok";
            headline = error ? "校验未通过" : warn ? "校验通过，有需要注意的地方" : "校验通过";
            return this;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("releaseId", release.getId());
            m.put("runtimeVersion", release.getRuntimeVersion());
            m.put("external", release.isExternal());
            m.put("level", level);
            m.put("headline", headline);
            m.put("checks", checks);
            return m;
        }

        public String getLevel() { return level; }
        public String getHeadline() { return headline; }
    }
}
