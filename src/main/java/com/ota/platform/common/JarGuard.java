package com.ota.platform.common;

import com.ota.platform.OtaPlatformApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.File;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 运行中的 jar 被覆盖后，JVM 再也加载不了新类，正常关闭会在半路出错，内置数据库可能因此损坏。
 * 发现 jar 变了就立刻把数据刷到磁盘；关闭时跳过数据库的收尾（相当于断电，内置数据库能完整恢复），直接退出。
 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "ota.demo.mode", havingValue = "false", matchIfMissing = true)
public class JarGuard implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(JarGuard.class);

    private final DataSource dataSource;
    private final File jar;
    private final long size;
    private final long modified;
    private ScheduledExecutorService timer;
    private volatile boolean h2;
    private volatile boolean replaced;
    private volatile boolean running;

    public JarGuard(DataSource dataSource) {
        this.dataSource = dataSource;
        File source = null;
        try {
            File f = new ApplicationHome(OtaPlatformApplication.class).getSource();
            if (f != null && f.isFile() && f.getName().endsWith(".jar")) source = f;
        } catch (Exception ignored) {
            // 在 IDE 里直接跑 classes 目录时没有 jar，不用看守
        }
        this.jar = source;
        this.size = source == null ? 0 : source.length();
        this.modified = source == null ? 0 : source.lastModified();
    }

    @Override
    public void start() {
        running = true;
        if (jar == null) return;
        // 先跑一遍要用到的语句，把相关类提前加载进来：jar 被覆盖以后就加载不了了
        h2 = checkpoint(false);
        timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ota-jar-guard");
            t.setDaemon(true);
            return t;
        });
        timer.scheduleWithFixedDelay(this::check, 2, 2, TimeUnit.SECONDS);
    }

    private void check() {
        if (replaced || (jar.length() == size && jar.lastModified() == modified)) return;
        replaced = true;
        boolean synced = h2 && checkpoint(true);
        log.warn("程序文件 {} 在运行中被替换了{}。请马上重启服务让新版本生效；以后请先停止服务再替换 jar，或者上传成新文件名再用 mv 覆盖",
                jar, synced ? "，数据已经写入磁盘" : "");
    }

    /** 内置数据库返回 true；外部数据库（MySQL）自己负责落盘，不用管 */
    private boolean checkpoint(boolean sync) {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            if (!c.getMetaData().getURL().startsWith("jdbc:h2:")) return false;
            st.execute(sync ? "CHECKPOINT SYNC" : "CHECKPOINT");
            return true;
        } catch (Throwable e) {
            return false;
        }
    }

    @Override
    public void stop() {
        running = false;
        if (timer != null) timer.shutdownNow();
        if (!replaced || !h2) return;
        checkpoint(true);
        log.warn("程序文件已被替换：数据已写入磁盘，跳过正常关闭直接退出");
        Runtime.getRuntime().halt(0);
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    /** 最先停：数据库连接池还没关的时候就要处理 */
    @Override
    public int getPhase() {
        return Integer.MAX_VALUE;
    }
}
