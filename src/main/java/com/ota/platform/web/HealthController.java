package com.ota.platform.web;

import com.ota.platform.app.OtaAppRepository;
import com.ota.platform.config.OtaProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/** 健康检查：给宝塔监控、负载均衡、容器探活用，不需要登录 */
@RestController
public class HealthController {

    private final OtaAppRepository apps;
    private final OtaProperties props;
    private final ObjectProvider<BuildProperties> build;

    public HealthController(OtaAppRepository apps, OtaProperties props, ObjectProvider<BuildProperties> build) {
        this.apps = apps;
        this.props = props;
        this.build = build;
    }

    @GetMapping("/healthz")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> m = new LinkedHashMap<>();
        boolean ok = true;
        try {
            apps.count();
            m.put("database", "ok");
        } catch (Exception e) {
            ok = false;
            m.put("database", "error");
        }
        m.put("storage", props.getStorage().getType());
        BuildProperties b = build.getIfAvailable();
        if (b != null) m.put("version", b.getVersion());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ok ? "ok" : "error");
        body.putAll(m);
        return ResponseEntity.status(ok ? 200 : 503).body(body);
    }
}
