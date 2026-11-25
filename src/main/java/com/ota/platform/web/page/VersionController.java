package com.ota.platform.web.page;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 开着的页面定时问一下现在的版本号，变了就提示重新加载（不用登录，只回版本号） */
@RestController
public class VersionController {

    private final BuildStamp build;

    public VersionController(BuildStamp build) {
        this.build = build;
    }

    @GetMapping("/app/version")
    public ResponseEntity<Map<String, String>> version() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("build", build.id(), "time", build.time().toString()));
    }
}
