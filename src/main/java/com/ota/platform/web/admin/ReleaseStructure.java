package com.ota.platform.web.admin;

import com.ota.platform.web.AdminUi;
import com.ota.platform.patch.Patch;
import com.ota.platform.patch.PatchRepository;
import com.ota.platform.release.Asset;
import com.ota.platform.release.AssetRepository;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseFile;
import com.ota.platform.release.ReleaseFileRepository;
import com.ota.platform.release.ReleaseRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 更新包结构：列出版本里的每个文件，和同一 Runtime 的某个更早版本比是新增、有变化还是没变。
 * 没变的文件设备里已经有了，更新时不用再下载；启动包有增量补丁时只下补丁。外部链接版本一样能看（文件在外部地址）。
 */
@Component
public class ReleaseStructure {

    private static final Map<String, String> LABELS = Map.of(
            "added", "新增", "changed", "有变化", "same", "没变", "moved", "没变（换了位置）", "deleted", "已删除", "none", "—");

    private final ReleaseRepository releases;
    private final ReleaseFileRepository files;
    private final AssetRepository assets;
    private final PatchRepository patches;
    private final AdminUi ui;

    public ReleaseStructure(ReleaseRepository releases, ReleaseFileRepository files, AssetRepository assets, PatchRepository patches, AdminUi ui) {
        this.releases = releases;
        this.files = files;
        this.assets = assets;
        this.patches = patches;
        this.ui = ui;
    }

    /** 可以拿来对比的版本：同一个 App、同一个 Runtime、更早发布的，最近 30 个 */
    public List<Release> bases(Release r) {
        return releases.findByAppIdAndRuntimeVersionAndCreatedAtBeforeOrderByCreatedAtDesc(r.getAppId(), r.getRuntimeVersion(),
                r.getCreatedAt(), PageRequest.of(0, 30));
    }

    public List<Map<String, Object>> baseOptions(List<Release> bases) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Release b : bases) {
            String msg = b.getMessage() == null ? "" : b.getMessage().trim();
            if (msg.length() > 30) msg = msg.substring(0, 30) + "…";
            Map<String, Object> o = new LinkedHashMap<>();
            o.put("id", b.getId());
            o.put("label", b.shortId() + " · " + ui.dt(b.getCreatedAt()) + (msg.isEmpty() ? "" : " · " + msg));
            out.add(o);
        }
        return out;
    }

    public Map<String, Object> build(Release r, Release base) {
        List<ReleaseFile> mine = files.findByReleaseIdOrderBySortOrderAsc(r.getId());
        List<ReleaseFile> theirs = base == null ? List.of() : files.findByReleaseIdOrderBySortOrderAsc(base.getId());
        Set<String> shas = new HashSet<>();
        mine.forEach(f -> shas.add(f.getAssetSha256()));
        theirs.forEach(f -> shas.add(f.getAssetSha256()));
        Map<String, Long> sizes = new HashMap<>();
        for (Asset a : assets.findAllById(shas)) sizes.put(a.getSha256(), a.getSize());

        Map<String, Set<String>> baseShas = new HashMap<>();
        Map<String, Map<String, String>> basePaths = new HashMap<>();
        Map<String, String> baseLaunch = new HashMap<>();
        for (ReleaseFile f : theirs) {
            baseShas.computeIfAbsent(f.getPlatform(), k -> new HashSet<>()).add(f.getAssetSha256());
            basePaths.computeIfAbsent(f.getPlatform(), k -> new HashMap<>()).put(f.getPath(), f.getAssetSha256());
            if (f.isLaunch()) baseLaunch.put(f.getPlatform(), f.getAssetSha256());
        }

        List<ReleaseFile> sorted = new ArrayList<>(mine);
        sorted.sort(Comparator.comparing(ReleaseFile::getPlatform).thenComparing(f -> !f.isLaunch()).thenComparing(ReleaseFile::getPath));
        Map<String, Integer> counts = new LinkedHashMap<>();
        Set<String> mineKeys = new HashSet<>();
        Map<String, Set<String>> mineShas = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        long total = 0, download = 0, saved = 0;
        boolean patched = false;
        for (ReleaseFile f : sorted) {
            mineKeys.add(f.getPlatform() + "\n" + f.getPath());
            mineShas.computeIfAbsent(f.getPlatform(), k -> new HashSet<>()).add(f.getAssetSha256());
            Long size = sizes.get(f.getAssetSha256());
            total += size == null ? 0 : size;
            String status;
            if (base == null) status = "none";
            else if (f.isLaunch() && baseLaunch.containsKey(f.getPlatform())) {
                // 启动包的文件名带内容哈希，每次构建都不一样：按平台和上一个启动包比
                status = f.getAssetSha256().equals(baseLaunch.get(f.getPlatform())) ? "same" : "changed";
            } else if (baseShas.getOrDefault(f.getPlatform(), Set.of()).contains(f.getAssetSha256())) {
                status = f.getAssetSha256().equals(basePaths.get(f.getPlatform()).get(f.getPath())) ? "same" : "moved";
            } else if (basePaths.getOrDefault(f.getPlatform(), Map.of()).containsKey(f.getPath())) status = "changed";
            else status = "added";
            String kind = "full";
            long bytes = size == null ? 0 : size;
            if (status.equals("same") || status.equals("moved")) {
                kind = "none";
                bytes = 0;
            } else if (f.isLaunch() && base != null && baseLaunch.containsKey(f.getPlatform())) {
                Patch p = patches.findByBaseShaAndTargetSha(baseLaunch.get(f.getPlatform()), f.getAssetSha256()).orElse(null);
                if (p != null && Patch.READY.equals(p.getStatus())) {
                    kind = "patch";
                    saved += bytes - p.getPatchSize();
                    bytes = p.getPatchSize();
                    patched = true;
                }
            }
            download += bytes;
            counts.merge(status, 1, Integer::sum);
            rows.add(row(f, size, status, kind, bytes));
        }
        Set<String> launchPlatforms = new HashSet<>();
        for (ReleaseFile f : mine) if (f.isLaunch()) launchPlatforms.add(f.getPlatform());
        List<ReleaseFile> gone = new ArrayList<>();
        for (ReleaseFile f : theirs) {
            if (f.isLaunch() && launchPlatforms.contains(f.getPlatform())) continue;
            if (!mineKeys.contains(f.getPlatform() + "\n" + f.getPath())
                    && !mineShas.getOrDefault(f.getPlatform(), Set.of()).contains(f.getAssetSha256())) gone.add(f);
        }
        gone.sort(Comparator.comparing(ReleaseFile::getPlatform).thenComparing(ReleaseFile::getPath));
        for (ReleaseFile f : gone) {
            counts.merge("deleted", 1, Integer::sum);
            rows.add(row(f, sizes.get(f.getAssetSha256()), "deleted", "none", 0));
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("base", base == null ? null : base.getId());
        out.put("baseShort", base == null ? null : base.shortId());
        out.put("rows", rows);
        out.put("files", mine.size());
        out.put("total", ui.size(total));
        out.put("download", ui.size(download));
        out.put("counts", counts);
        String summary;
        if (base == null) {
            summary = "共 " + mine.size() + " 个文件（" + ui.size(total) + "）。没有拿来对比的版本，设备第一次更新要下载全部文件";
        } else {
            List<String> parts = new ArrayList<>();
            for (String k : List.of("added", "changed", "same", "moved", "deleted")) {
                if (counts.containsKey(k)) parts.add(LABELS.get(k) + " " + counts.get(k) + " 个");
            }
            summary = "共 " + mine.size() + " 个文件（" + ui.size(total) + "）。和 " + base.shortId() + " 比：" + String.join("、", parts)
                    + "。从 " + base.shortId() + " 更新过来要下载 " + ui.size(download)
                    + (patched ? "，启动包用增量补丁，少下 " + ui.size(saved) : "");
        }
        out.put("summary", summary);
        return out;
    }

    private Map<String, Object> row(ReleaseFile f, Long size, String status, String kind, long bytes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("platform", f.getPlatform());
        m.put("path", f.getPath());
        m.put("launch", f.isLaunch());
        m.put("sha256", f.getAssetSha256());
        m.put("size", size == null ? "—" : ui.size(size));
        m.put("status", status);
        m.put("statusLabel", LABELS.get(status));
        m.put("kind", kind);
        m.put("download", switch (kind) {
            case "none" -> status.equals("deleted") ? "—" : "不用下载";
            case "patch" -> "补丁 " + ui.size(bytes);
            default -> size == null ? "—" : ui.size(bytes);
        });
        return m;
    }
}
