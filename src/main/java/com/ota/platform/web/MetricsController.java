package com.ota.platform.web;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Prometheus 采集地址：GET /api/metrics，请求头 Authorization: Bearer 监控令牌。
 * 不在 /api/v1 下面，管理接口的令牌校验不管它；监控令牌只能读指标，在「系统配置 → 运行信息」里生成。
 */
@RestController
public class MetricsController {

    private static final MediaType TEXT = MediaType.parseMediaType("text/plain; version=0.0.4; charset=utf-8");

    private final MetricsService metrics;

    public MetricsController(MetricsService metrics) {
        this.metrics = metrics;
    }

    @GetMapping("/api/metrics")
    public ResponseEntity<String> metrics(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String auth) {
        if (!metrics.enabled()) {
            return ResponseEntity.status(404).contentType(TEXT).body("# 监控指标没有开启：在后台「系统配置 → 运行信息」里生成监控令牌\n");
        }
        String token = auth != null && auth.regionMatches(true, 0, "Bearer ", 0, 7) ? auth.substring(7).trim() : null;
        if (!metrics.matches(token)) {
            return ResponseEntity.status(401).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").contentType(TEXT)
                    .body("# 监控令牌不对：请求头要带 Authorization: Bearer 监控令牌\n");
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(TEXT).body(metrics.render());
    }
}
