package com.ota.platform.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

/** 旧版本的操作日志带 operator_ip 列，升级后第一次启动时连同里面的 IP 一起删掉 */
@Component
public class LogIpCleanup {

    private static final Logger log = LoggerFactory.getLogger(LogIpCleanup.class);

    private final JdbcTemplate jdbc;

    public LogIpCleanup(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dropIpColumn() {
        try {
            Boolean exists = jdbc.execute((ConnectionCallback<Boolean>) con -> {
                DatabaseMetaData md = con.getMetaData();
                for (String[] name : new String[][]{{"operation_log", "operator_ip"}, {"OPERATION_LOG", "OPERATOR_IP"}}) {
                    try (ResultSet rs = md.getColumns(con.getCatalog(), null, name[0], name[1])) {
                        if (rs.next()) return true;
                    }
                }
                return false;
            });
            if (Boolean.TRUE.equals(exists)) {
                jdbc.execute("ALTER TABLE operation_log DROP COLUMN operator_ip");
                log.info("操作日志不再记录 IP，已删除 operation_log.operator_ip 列和其中的数据");
            }
        } catch (Exception e) {
            log.warn("删除操作日志的 IP 列失败：{}", e.toString());
        }
    }
}
