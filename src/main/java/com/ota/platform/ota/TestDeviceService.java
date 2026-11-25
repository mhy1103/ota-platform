package com.ota.platform.ota;

import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.common.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** 测试设备：检查更新时按设备 ID 换成指定渠道。名单放在内存里，改了立即生效 */
@Service
public class TestDeviceService {

    public static final int MAX_PER_APP = 500;

    private record Tester(String channel, boolean rolloutFirst) {}

    private final TestDeviceRepo repo;
    private final AppService apps;
    private final Map<Long, Map<String, Tester>> cache = new ConcurrentHashMap<>();

    public TestDeviceService(TestDeviceRepo repo, AppService apps) {
        this.repo = repo;
        this.apps = apps;
    }

    /** 这台设备是测试设备时返回它要用的渠道。iOS 的设备 ID 是大写，按不分大小写比较 */
    public Optional<String> channelFor(OtaApp app, String clientId) {
        return find(app, clientId).map(Tester::channel);
    }

    /** 这台设备在灰度白名单里：灰度没到 100% 时也先拿线上版本 */
    public boolean rolloutFirst(OtaApp app, String clientId) {
        return find(app, clientId).map(Tester::rolloutFirst).orElse(false);
    }

    private Optional<Tester> find(OtaApp app, String clientId) {
        if (clientId == null || clientId.isBlank()) return Optional.empty();
        Map<String, Tester> m = cache.computeIfAbsent(app.getId(), this::load);
        return Optional.ofNullable(m.get(key(clientId)));
    }

    public List<TestDevice> list(OtaApp app) {
        return repo.findByAppIdOrderByCreatedAtDesc(app.getId());
    }

    /** 添加或修改：同一台设备再加一次就是改名字和渠道；灰度白名单不变（新加的不在白名单里） */
    @Transactional
    public TestDevice save(OtaApp app, String clientId, String name, String channel) {
        return save(app, clientId, name, channel, null);
    }

    /** rolloutFirst 为空时不改原来的设置 */
    @Transactional
    public TestDevice save(OtaApp app, String clientId, String name, String channel, Boolean rolloutFirst) {
        String cid = clientId == null ? "" : clientId.trim();
        if (cid.isEmpty() || cid.length() > 100) throw ApiException.badRequest("BAD_DEVICE", "设备 ID 不能为空，最多 100 位");
        String ch = channel == null ? "" : channel.trim();
        apps.requireChannel(app, ch);
        String n = name == null || name.isBlank() ? null : (name.trim().length() > 50 ? name.trim().substring(0, 50) : name.trim());
        TestDevice d = repo.findByAppIdOrderByCreatedAtDesc(app.getId()).stream()
                .filter(t -> key(t.getClientId()).equals(key(cid))).findFirst().orElse(null);
        if (d == null) {
            if (repo.countByAppId(app.getId()) >= MAX_PER_APP) {
                throw ApiException.badRequest("TOO_MANY", "每个 App 最多 " + MAX_PER_APP + " 台测试设备");
            }
            d = new TestDevice(app.getId(), cid, n, ch);
        } else {
            d.setName(n);
            d.setChannel(ch);
        }
        if (rolloutFirst != null) d.setRolloutFirst(rolloutFirst);
        repo.save(d);
        cache.remove(app.getId());
        return d;
    }

    @Transactional
    public TestDevice remove(OtaApp app, Long id) {
        TestDevice d = repo.findById(id == null ? -1L : id).filter(t -> t.getAppId().equals(app.getId()))
                .orElseThrow(() -> ApiException.notFound("DEVICE_NOT_FOUND", "测试设备不存在"));
        repo.delete(d);
        cache.remove(app.getId());
        return d;
    }

    /** 渠道删除后，指向它的测试设备也删掉 */
    @Transactional
    public int dropChannel(OtaApp app, String channel) {
        int n = repo.deleteByAppIdAndChannel(app.getId(), channel);
        cache.remove(app.getId());
        return n;
    }

    @Transactional
    public void dropApp(Long appId) {
        repo.deleteByAppId(appId);
        cache.remove(appId);
    }

    private Map<String, Tester> load(Long appId) {
        Map<String, Tester> m = new HashMap<>();
        for (TestDevice t : repo.findByAppIdOrderByCreatedAtDesc(appId)) m.put(key(t.getClientId()), new Tester(t.getChannel(), t.isRolloutFirst()));
        return m;
    }

    private static String key(String clientId) {
        return clientId.trim().toLowerCase(Locale.ROOT);
    }
}
