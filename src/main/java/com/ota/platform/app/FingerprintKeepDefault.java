package com.ota.platform.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 设备指纹保留以前默认一直留着（0），现在默认 7 天。升级后第一次启动时，把还是老默认值的 App 改成 7 天；
 * 在 App 设置里特意选过「一直留着」的（操作日志里有）不动。只做一次，做过记在 app_setting 的 fp.keep.default7
 */
@Component
public class FingerprintKeepDefault {

    private static final Logger log = LoggerFactory.getLogger(FingerprintKeepDefault.class);
    static final String KEY = "fp.keep.default7";

    private final JdbcTemplate jdbc;

    public FingerprintKeepDefault(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void apply() {
        try {
            Integer done = jdbc.queryForObject("select count(*) from app_setting where setting_key = ?", Integer.class, KEY);
            if (done != null && done > 0) return;
            int n = jdbc.update("update ota_app set fingerprint_keep_days = ? where (fingerprint_keep_days is null or fingerprint_keep_days = 0)"
                    + " and not exists (select 1 from operation_log l where l.detail = concat(ota_app.slug, ' 设备指纹保留一直留着'))",
                    OtaApp.DEFAULT_FINGERPRINT_KEEP_DAYS);
            jdbc.update("insert into app_setting (setting_key, setting_value) values (?, ?)", KEY, "1");
            if (n > 0) log.info("设备指纹保留默认改成 {} 天：{} 个 App 从「一直留着」改了过来，特意选过一直留着的没动", OtaApp.DEFAULT_FINGERPRINT_KEEP_DAYS, n);
        } catch (Exception e) {
            log.warn("设备指纹保留的默认天数没改成，下次启动再试：{}", e.toString());
        }
    }
}
