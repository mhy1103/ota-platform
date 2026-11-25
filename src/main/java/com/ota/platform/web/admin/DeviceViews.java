package com.ota.platform.web.admin;

import com.ota.platform.web.AdminUi;
import com.ota.platform.app.OtaApp;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.stats.StatsService;
import org.springframework.stereotype.Component;

import java.util.*;

/** 设备页和概览共用：近 30 天设备的版本分布，以及有多少设备已经在运行线上版本。 */
@Component
public class DeviceViews {

    private final StatsService stats;
    private final ReleaseService releases;
    private final ReleaseViews releaseViews;
    private final AdminUi ui;

    public DeviceViews(StatsService stats, ReleaseService releases, ReleaseViews releaseViews, AdminUi ui) {
        this.stats = stats;
        this.releases = releases;
        this.releaseViews = releaseViews;
        this.ui = ui;
    }

    /** 版本分布的一行 */
    public static class VersionBar {
        private final String key;
        private final String label;
        private final String sub;
        private final long count;
        private final int percent;
        private final boolean live;
        private final String tag;

        VersionBar(String key, String label, String sub, long count, int percent, boolean live, String tag) {
            this.key = key;
            this.label = label;
            this.sub = sub;
            this.count = count;
            this.percent = percent;
            this.live = live;
            this.tag = tag;
        }

        /** 筛选用的值：版本 ID、embedded 或 unknown */
        public String getKey() { return key; }
        public String getLabel() { return label; }
        public String getSub() { return sub; }
        public long getCount() { return count; }
        public int getPercent() { return percent; }
        public boolean isLive() { return live; }
        /** 「线上」「线上 · 30%」「上一版本 · 70%」，不在渠道上是 null */
        public String getTag() { return tag; }
    }

    /** tags：版本 ID → 渠道上的角色；previousIds：灰度中还在给其余设备用的上一版本 */
    public record Distribution(long active, long upToDate, List<VersionBar> bars, Map<String, String> labels, Set<String> liveIds,
                               Set<String> previousIds, Map<String, String> tags) {
        public int upToDatePercent() {
            return pct(upToDate, active);
        }

        public boolean hasLive() {
            return !liveIds.isEmpty();
        }
    }

    public Distribution distribution(OtaApp app) {
        Set<String> liveIds = new HashSet<>();
        Set<String> previousIds = new HashSet<>();
        Map<String, String> tags = new HashMap<>();
        for (ReleaseViews.Live l : releaseViews.live(app)) {
            if (l.getRelease() == null || l.isRollbackToEmbedded()) continue;
            String id = l.getRelease().getId();
            liveIds.add(id);
            tags.put(id, l.getRollout() < 100 ? "线上 · " + l.getRollout() + "%" : "线上");
            if (l.getRollout() < 100 && l.getPrevious() != null) {
                previousIds.add(l.getPrevious().getId());
                tags.putIfAbsent(l.getPrevious().getId(), "上一版本 · " + (100 - l.getRollout()) + "%");
            }
        }
        long active = stats.activeDevices(app.getId());
        Map<String, String> labels = new HashMap<>();
        List<VersionBar> bars = new ArrayList<>();
        long unknown = 0;
        long upToDate = 0;
        for (Map.Entry<String, Long> e : stats.devicesByUpdate(app.getId()).entrySet()) {
            String id = e.getKey();
            long n = e.getValue();
            if (StatsService.EMBEDDED.equals(id)) {
                bars.add(new VersionBar(id, "安装包内置版本", "还没有装上任何热更新", n, pct(n, active), false, null));
                continue;
            }
            Optional<Release> r = id.isEmpty() ? Optional.empty() : releases.find(id).filter(x -> x.getAppId().equals(app.getId()));
            if (r.isEmpty()) {
                unknown += n;
                continue;
            }
            Release rel = r.get();
            boolean live = liveIds.contains(rel.getId());
            if (live) upToDate += n;
            labels.put(id, rel.shortId());
            String sub = "Runtime " + rel.getRuntimeVersion() + (rel.getMessage() != null ? " · " + rel.getMessage() : "") + " · " + ui.dt(rel.getCreatedAt());
            bars.add(new VersionBar(id, rel.shortId(), sub, n, pct(n, active), live, tags.get(id)));
        }
        bars.sort(Comparator.comparingLong(VersionBar::getCount).reversed());
        if (unknown > 0) {
            bars.add(new VersionBar("unknown", "其他", "版本已删除、安装包内置的版本，或没有上报当前版本", unknown, pct(unknown, active), false, null));
        }
        return new Distribution(active, upToDate, bars, labels, liveIds, previousIds, tags);
    }

    static int pct(long n, long total) {
        return com.ota.platform.common.Percent.of(n, total);
    }
}
