package com.ota.platform.audit;

import com.ota.platform.settings.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 按保留天数每天清理操作日志 */
@Component
public class LogRetention {

    private static final Logger log = LoggerFactory.getLogger(LogRetention.class);

    private final OperationLogRepo logs;
    private final SettingService settings;

    public LogRetention(OperationLogRepo logs, SettingService settings) {
        this.logs = logs;
        this.settings = settings;
    }

    @Scheduled(cron = "0 20 4 * * *")
    public void daily() {
        int n = clean();
        if (n > 0) log.info("清理了 {} 条超过 {} 天的操作日志", n, settings.logKeepDays());
    }

    /** 返回删了几条 */
    public int clean() {
        int days = settings.logKeepDays();
        if (days <= 0) return 0;
        return logs.deleteOlderThan(LocalDateTime.now().minusDays(days));
    }
}
