package com.ota.platform.web.admin;

import com.ota.platform.app.AppService;
import com.ota.platform.app.Channel;
import com.ota.platform.app.OtaApp;
import com.ota.platform.ota.ReleaseStat;
import com.ota.platform.ota.ReleaseStatsService;
import com.ota.platform.release.Deployment;
import com.ota.platform.release.Release;
import com.ota.platform.release.ReleaseService;
import com.ota.platform.stats.StatsService;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/** 版本发布页和概览要用的视图数据：每个渠道的线上版本，以及按 Runtime 分组的版本列表。 */
@Component
public class ReleaseViews {

    private final AppService apps;
    private final ReleaseService releases;
    private final StatsService stats;

    public ReleaseViews(AppService apps, ReleaseService releases, StatsService stats) {
        this.apps = apps;
        this.releases = releases;
        this.stats = stats;
    }

    /** 启动失败偏多的线上版本；同一个版本在几个渠道上线只算一条，lives 是它所在的每个渠道 */
    public record Alarm(Release release, ReleaseStat stat, List<Live> lives) {
        public String channels() {
            return String.join("、", lives.stream().map(Live::getChannel).toList());
        }
    }

    public static List<Alarm> alarms(List<Live> live, Map<String, ReleaseStat> stats) {
        Map<String, List<Live>> byRelease = new LinkedHashMap<>();
        for (Live l : live) {
            if (l.getRelease() == null || l.isRollbackToEmbedded() || !ReleaseStatsService.alarming(stats.get(l.getRelease().getId()))) continue;
            byRelease.computeIfAbsent(l.getRelease().getId(), k -> new ArrayList<>()).add(l);
        }
        List<Alarm> out = new ArrayList<>();
        byRelease.forEach((id, ls) -> out.add(new Alarm(ls.get(0).getRelease(), stats.get(id), ls)));
        return out;
    }

    /** 渠道在某个 Runtime 下的线上状态 */
    public static class Live {
        private final String channel;
        private final Deployment deployment;
        private final Release release;
        private final Release previous;
        private final Release activation;

        Live(String channel, Deployment deployment, Release release, Release previous, Release activation) {
            this.channel = channel;
            this.deployment = deployment;
            this.release = release;
            this.previous = previous;
            this.activation = activation;
        }

        public String getChannel() { return channel; }
        public String getRuntime() { return deployment.getRuntimeVersion(); }
        public Release getRelease() { return release; }
        public Release getPrevious() { return previous; }
        public int getRollout() { return deployment.getRolloutPercent(); }
        public boolean isRollbackToEmbedded() { return deployment.isRollbackToEmbedded(); }
        public Instant getRollbackAt() { return deployment.getRollbackAt(); }
        public Instant getUpdatedAt() { return deployment.getUpdatedAt(); }
        public String getUpdatedBy() { return deployment.getUpdatedBy(); }
        /** 单独设的口令更新包：还在内置版本的设备（输入口令激活的）拿它；没设时为 null，这些设备拿线上版本 */
        public Release getActivation() { return activation; }
        public Instant getActivationAt() { return deployment.getActivationAt(); }
        public String getActivationBy() { return deployment.getActivationBy(); }
        /** 最近操作：常规更新和口令更新包哪个后改算哪个 */
        public Instant getLastAt() { return activationLater() ? deployment.getActivationAt() : deployment.getUpdatedAt(); }
        public String getLastBy() { return activationLater() ? deployment.getActivationBy() : deployment.getUpdatedBy(); }

        private boolean activationLater() {
            Instant a = deployment.getActivationAt(), u = deployment.getUpdatedAt();
            return a != null && (u == null || a.isAfter(u) || release == null && !deployment.isRollbackToEmbedded());
        }
    }

    public static class Row {
        private final Release release;
        private final List<String> liveIn;
        private final List<String> previousIn;
        private final List<String> activationIn;
        private final boolean inUse;
        private final String lock;
        private final long devices;

        Row(Release release, List<String> liveIn, List<String> previousIn, List<String> activationIn, boolean inUse, String lock, long devices) {
            this.release = release;
            this.liveIn = liveIn;
            this.previousIn = previousIn;
            this.activationIn = activationIn;
            this.inUse = inUse;
            this.lock = lock;
            this.devices = devices;
        }

        public Release getRelease() { return release; }
        /** 「production · 30%」这样的线上标记 */
        public List<String> getLiveIn() { return liveIn; }
        /** 「production 上一版本 · 70%」：可以回滚到它，灰度中还给其余设备用 */
        public List<String> getPreviousIn() { return previousIn; }
        /** 把它设成了口令更新包的渠道 */
        public List<String> getActivationIn() { return activationIn; }
        public boolean isLive() { return !liveIn.isEmpty(); }
        /** 线上或某个渠道的上一版本（列表里总是列出来） */
        public boolean isInUse() { return inUse; }
        /** 还有设备在拿、不能删除的原因；能删时是 null */
        public String getLock() { return lock; }
        public long getDevices() { return devices; }
    }

    public static class Group {
        private final String runtime;
        private final List<Row> rows;
        private final int total;

        Group(String runtime, List<Row> rows, int total) {
            this.runtime = runtime;
            this.rows = rows;
            this.total = total;
        }

        public String getRuntime() { return runtime; }
        public List<Row> getRows() { return rows; }
        /** 这个 Runtime 的版本总数，rows 可能只是最近的一部分 */
        public int getTotal() { return total; }
        public int getHidden() { return total - rows.size(); }
    }

    /**
     * 回滚弹窗里可以选的版本：每个 Runtime 一组。复制出来的版本和原版本内容相同，合成一项（用原版本的 ID），
     * 按这份内容最近一次发布的时间排，最新的在前，每组最多 60 个。
     */
    public record Choice(String id, String origin, String message, Instant at, long devices, int copies) {}

    public Map<String, List<Choice>> choices(OtaApp app) {
        Map<String, Long> devices = stats.devicesByUpdate(app.getId());
        Map<String, Map<String, List<Release>>> byRuntime = new LinkedHashMap<>();
        for (Release r : releases.all(app)) {
            byRuntime.computeIfAbsent(r.getRuntimeVersion(), k -> new LinkedHashMap<>())
                    .computeIfAbsent(ReleaseService.origin(r), k -> new ArrayList<>()).add(r);
        }
        Map<String, List<Choice>> out = new LinkedHashMap<>();
        byRuntime.forEach((rv, groups) -> {
            List<Choice> list = new ArrayList<>();
            groups.forEach((origin, rs) -> {
                Release shown = rs.stream().filter(r -> r.getId().equals(origin)).findFirst().orElse(rs.get(0));
                long dev = rs.stream().mapToLong(r -> devices.getOrDefault(r.getId(), 0L)).sum();
                String message = shown.getMessage() != null ? shown.getMessage() : rs.get(0).getMessage();
                list.add(new Choice(shown.getId(), origin, message, rs.get(0).getCreatedAt(), dev, rs.size() - 1));
            });
            out.put(rv, list.size() > 60 ? new ArrayList<>(list.subList(0, 60)) : list);
        });
        return out;
    }

    /** 这个 Runtime 下已经没有更新包（线上版本、口令更新包都没了）的不列：回退内置版本以后把版本删了的，只在后台继续下发回退指令 */
    public List<Live> live(OtaApp app) {
        List<Live> out = new ArrayList<>();
        for (Channel c : apps.channels(app)) {
            for (Deployment d : releases.deployments(c)) {
                Release r = releases.find(d.getReleaseId()).orElse(null);
                Release a = releases.find(d.getActivationReleaseId()).orElse(null);
                if (r == null && a == null) continue;
                Release p = releases.find(d.getPreviousReleaseId()).orElse(null);
                out.add(new Live(c.getName(), d, r, p, a));
            }
        }
        return out;
    }

    /** 按 Runtime 分组，每组列出最近 limit 个版本；线上、上一版本和口令更新包超出 limit 也列出 */
    public List<Group> groups(OtaApp app, List<Live> live, int limit) {
        Map<String, List<String>> liveIn = new HashMap<>();
        Map<String, List<String>> previousIn = new HashMap<>();
        Map<String, List<String>> activationIn = new HashMap<>();
        Set<String> inUse = new HashSet<>();
        Map<String, String> locks = new HashMap<>();
        for (Live l : live) {
            if (l.getActivation() != null) {
                String id = l.getActivation().getId();
                inUse.add(id);
                activationIn.computeIfAbsent(id, k -> new ArrayList<>()).add(l.getChannel());
            }
            if (!l.isRollbackToEmbedded()) {
                if (l.getRelease() != null) locks.putIfAbsent(l.getRelease().getId(), "这是 " + l.getChannel() + " 的线上版本，不能删除");
                if (l.getPrevious() != null && l.getRollout() < 100) {
                    locks.putIfAbsent(l.getPrevious().getId(), l.getChannel() + " 灰度 " + l.getRollout() + "%，其余 " + (100 - l.getRollout()) + "% 的设备还在用这个版本，不能删除");
                }
            }
            if (l.getRelease() != null) {
                inUse.add(l.getRelease().getId());
                if (!l.isRollbackToEmbedded()) {
                    liveIn.computeIfAbsent(l.getRelease().getId(), k -> new ArrayList<>())
                            .add(l.getChannel() + (l.getRollout() < 100 ? " · " + l.getRollout() + "%" : ""));
                }
            }
            if (l.getPrevious() != null) {
                inUse.add(l.getPrevious().getId());
                if (!l.isRollbackToEmbedded()) {
                    previousIn.computeIfAbsent(l.getPrevious().getId(), k -> new ArrayList<>())
                            .add(l.getChannel() + " 上一版本" + (l.getRollout() < 100 ? " · " + (100 - l.getRollout()) + "%" : ""));
                }
            }
        }
        for (Live l : live) {
            if (l.getActivation() != null) {
                locks.putIfAbsent(l.getActivation().getId(), "这是 " + l.getChannel() + " 的口令更新包，输入口令激活的设备在下载它，不能删除。先换一个或取消口令更新包");
            }
        }
        Map<String, Long> devices = stats.devicesByUpdate(app.getId());
        Map<String, List<Row>> byRuntime = new LinkedHashMap<>();
        for (Release r : releases.all(app)) {
            byRuntime.computeIfAbsent(r.getRuntimeVersion(), k -> new ArrayList<>())
                    .add(new Row(r, liveIn.getOrDefault(r.getId(), List.of()), previousIn.getOrDefault(r.getId(), List.of()),
                            activationIn.getOrDefault(r.getId(), List.of()), inUse.contains(r.getId()), locks.get(r.getId()),
                            devices.getOrDefault(r.getId(), 0L)));
        }
        List<Group> out = new ArrayList<>();
        byRuntime.forEach((rv, rows) -> {
            List<Row> shown = new ArrayList<>();
            for (int i = 0; i < rows.size(); i++) {
                if (i < limit || rows.get(i).isInUse()) shown.add(rows.get(i));
            }
            out.add(new Group(rv, shown, rows.size()));
        });
        return out;
    }
}
