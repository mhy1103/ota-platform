package com.ota.platform.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

/** 通知发送记录：每个 App 留最近 KEEP 条，在「App 设置 → 通知」里看哪次发出去了、哪次没发出去、为什么 */
@Service
public class NotifyDeliveries {

    private static final Logger log = LoggerFactory.getLogger(NotifyDeliveries.class);
    static final int KEEP = 200;

    private final NotifyDeliveryRepo repo;

    public NotifyDeliveries(NotifyDeliveryRepo repo) {
        this.repo = repo;
    }

    public void record(Long appId, String channel, String event, String url, boolean ok, String result, int attempts) {
        if (appId == null) return;
        try {
            repo.save(new NotifyDelivery(appId, channel, cut(event, 80), cut(host(url), 120), ok, cut(result, 300), attempts));
            if (repo.countByAppId(appId) > KEEP + 50) {
                List<NotifyDelivery> edge = repo.findByAppIdOrderByIdDesc(appId, PageRequest.of(KEEP - 1, 1));
                if (!edge.isEmpty()) repo.deleteOlder(appId, edge.get(0).getId());
            }
        } catch (Exception e) {
            log.debug("记录通知结果失败：{}", e.toString());
        }
    }

    public List<NotifyDelivery> recent(Long appId) {
        return repo.findTop30ByAppIdOrderByIdDesc(appId);
    }

    /** 只留域名：Webhook 和机器人地址里常带着令牌 */
    static String host(String url) {
        try {
            String h = URI.create(url.trim()).getHost();
            return h == null ? "" : h;
        } catch (Exception e) {
            return "";
        }
    }

    private static String cut(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
