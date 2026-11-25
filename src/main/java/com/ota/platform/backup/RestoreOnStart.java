package com.ota.platform.backup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.nio.file.Path;

/**
 * 启动（包括后台里点「恢复并重启」的进程内重启）时，在打开数据库、读 install.json 之前，
 * 把准备好的项目备份换上去。演示环境不做（它的数据每次都从默认演示数据开始）。
 */
public class RestoreOnStart implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        if ("true".equals(env.getProperty("ota.demo.mode"))) return;
        ProjectBackupService.applyPending(Path.of(env.getProperty("ota.data-dir", "./data")).toAbsolutePath().normalize());
    }

    /** 在 InstallPropertySource（最后一个）之前：恢复的 install.json 要被它读到 */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }
}
