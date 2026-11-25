package com.ota.platform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 定时任务：按天的设备数清理、自动备份、计划任务、演示环境空闲停止 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
