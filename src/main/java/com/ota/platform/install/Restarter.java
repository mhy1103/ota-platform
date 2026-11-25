package com.ota.platform.install;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

/**
 * 不退出进程重启整个应用：关掉当前的 Spring 上下文（端口、数据库连接都释放），再用原来的启动参数启动一次。
 * 安装、重新安装后换数据库、换存储都靠它生效。
 */
@Component
public class Restarter {

    private static final Logger log = LoggerFactory.getLogger(Restarter.class);
    private static volatile Class<?> mainClass;
    private static volatile String[] args = new String[0];

    private final ConfigurableApplicationContext context;
    private final long boot = System.currentTimeMillis();
    private volatile boolean restarting;

    public Restarter(ConfigurableApplicationContext context) {
        this.context = context;
    }

    public static void remember(Class<?> main, String[] a) {
        mainClass = main;
        args = a == null ? new String[0] : a.clone();
    }

    public boolean restarting() {
        return restarting;
    }

    /** 这次启动的时间。页面等重启时比较它，变了就是重启好了（服务停下的那一下可能没轮询到） */
    public long boot() {
        return boot;
    }

    /** 稍等一下再重启，让触发重启的这次请求先把响应发出去 */
    public synchronized void restartSoon() {
        // 演示环境是在同一个进程里另起的一个应用，不能用正式环境的启动参数重启
        if (restarting || mainClass == null || "true".equals(context.getEnvironment().getProperty("ota.demo.mode"))) return;
        restarting = true;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(800);
                log.info("应用配置已更新，正在重启……");
                context.close();
                SpringApplication.run(mainClass, args);
            } catch (Throwable e) {
                log.error("重启失败，请手动重启服务", e);
            }
        }, "ota-restart");
        t.setContextClassLoader(loader);
        t.setDaemon(false);
        t.start();
    }
}
