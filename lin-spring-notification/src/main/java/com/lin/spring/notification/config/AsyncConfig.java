package com.lin.spring.notification.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 启用异步任务（本地模式进程内投递）。
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
