package com.ota.platform.codes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 按键（一般是 IP）的滑动窗口限流，每分钟最多 limit 次。 */
public class RateLimiter {

    private final int limit;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter(int perMinute) {
        this.limit = perMinute;
    }

    public int limit() {
        return limit;
    }

    /** 超限返回 true；没超限时记一次 */
    public boolean limited(String key) {
        long now = System.currentTimeMillis();
        if (hits.size() > 50_000) evictStale(now);
        Deque<Long> q = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > 60_000) q.pollFirst();
            if (q.size() >= limit) return true;
            q.addLast(now);
            return false;
        }
    }

    /** 只丢一分钟内没再来过的键。全部清空等于给正在被限流的人解封 */
    private void evictStale(long now) {
        hits.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            synchronized (q) {
                return q.isEmpty() || now - q.peekLast() > 60_000;
            }
        });
        if (hits.size() > 200_000) hits.clear();
    }
}
