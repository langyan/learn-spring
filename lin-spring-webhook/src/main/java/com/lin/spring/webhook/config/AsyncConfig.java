package com.lin.spring.webhook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 投递 worker 线程池。
 *
 * <p>这是一个<b>固定大小</b>的池：面试追问 2 的噪声邻居正是靠打满它来拖垮整个平台的。
 * 本模块用「每商户并发配额 + 每目的地断路器」来保证它不会被单个商户占满。</p>
 */
@Configuration
public class AsyncConfig {

	@Bean("webhookWorkerExecutor")
	public Executor webhookWorkerExecutor(WebhookProperties properties) {
		int threads = Math.max(1, properties.getDelivery().getWorkerThreads());
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(threads);
		executor.setMaxPoolSize(threads);
		executor.setQueueCapacity(0);
		executor.setThreadNamePrefix("webhook-worker-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(5);
		executor.initialize();
		return executor;
	}
}
