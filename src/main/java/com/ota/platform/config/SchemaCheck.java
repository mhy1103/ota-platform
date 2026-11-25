package com.ota.platform.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.ota.platform.web.page.BuildStamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 启动快一点：表结构（Hibernate 的 ddl-auto=update）只在换了 jar 以后第一次启动时检查、补齐。
 * 这一步在 MySQL 上要把每张表的列、索引、外键查一遍，库里表多时要好几秒到几十秒。
 * 检查过的版本号记在库里（app_setting 的 schema.build），同一个版本再重启就跳过；恢复的备份带着它自己的记录，版本对不上照样检查
 */
@Component
public class SchemaCheck implements HibernatePropertiesCustomizer {

    private static final Logger log = LoggerFactory.getLogger(SchemaCheck.class);
    static final String KEY = "schema.build";

    private final DataSource dataSource;
    private final BuildStamp build;
    private final Environment env;
    private volatile boolean skipped;
    /** 检查表结构时 Hibernate 报过错（它只记日志、不停下来），这次就不记版本，下次启动再检查 */
    private final AtomicBoolean trouble = new AtomicBoolean();
    private AppenderBase<ILoggingEvent> watcher;

    public SchemaCheck(DataSource dataSource, BuildStamp build, Environment env) {
        this.dataSource = dataSource;
        this.build = build;
        this.env = env;
    }

    @Override
    public void customize(Map<String, Object> props) {
        if (!"update".equals(env.getProperty("spring.jpa.hibernate.ddl-auto", "update"))) return;
        if (build.id().equals(stored())) {
            props.put("hibernate.hbm2ddl.auto", "none");
            skipped = true;
            log.info("这个版本（{}）的表结构检查过了，跳过", build.id());
        } else {
            watch();
        }
    }

    private void watch() {
        if (!(LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx)) return;
        watcher = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent e) {
                if (e.getLevel().isGreaterOrEqual(Level.WARN)) trouble.set(true);
            }
        };
        watcher.setContext(ctx);
        watcher.start();
        ctx.getLogger("org.hibernate.tool.schema").addAppender(watcher);
    }

    /** 启动好了：这个版本的表结构检查、补齐过了 */
    @EventListener(ApplicationReadyEvent.class)
    public void remember() {
        if (watcher != null && LoggerFactory.getILoggerFactory() instanceof LoggerContext ctx) {
            ctx.getLogger("org.hibernate.tool.schema").detachAppender(watcher);
            watcher.stop();
        }
        if (skipped) return;
        if (trouble.get()) {
            log.warn("检查表结构时有语句没执行成功（见上面的日志），下次启动再检查一遍");
            return;
        }
        try (Connection c = dataSource.getConnection()) {
            int n;
            try (PreparedStatement up = c.prepareStatement("update app_setting set setting_value = ? where setting_key = ?")) {
                up.setString(1, build.id());
                up.setString(2, KEY);
                n = up.executeUpdate();
            }
            if (n == 0) {
                try (PreparedStatement in = c.prepareStatement("insert into app_setting (setting_key, setting_value) values (?, ?)")) {
                    in.setString(1, KEY);
                    in.setString(2, build.id());
                    in.executeUpdate();
                }
            }
        } catch (Exception e) {
            log.warn("记不下检查过的表结构版本，下次启动照常检查：{}", e.toString());
        }
    }

    private String stored() {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("select setting_value from app_setting where setting_key = ?")) {
            ps.setString(1, KEY);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
