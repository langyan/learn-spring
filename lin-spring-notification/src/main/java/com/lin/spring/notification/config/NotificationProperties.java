package com.lin.spring.notification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通知系统可配置项（Topic、重试、Provider 限流等）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.notification")
public class NotificationProperties {

	private Topics topics = new Topics();

	private Retry retry = new Retry();

	private Idempotency idempotency = new Idempotency();

	private Scheduler scheduler = new Scheduler();

	private Provider provider = new Provider();

	/**
	 * Kafka Topic 名称集合。
	 */
	@Data
	public static class Topics {
		private String critical = "notification.critical";
		private String transactional = "notification.transactional";
		private String marketing = "notification.marketing";
		private String retry = "notification.retry";
		private String dlq = "notification.dlq";
	}

	/**
	 * 重试策略配置。
	 */
	@Data
	public static class Retry {
		private int maxAttempts = 5;
		private List<Integer> backoffSeconds = List.of(10, 30, 60, 300);
	}

	/**
	 * 幂等键 TTL 配置。
	 */
	@Data
	public static class Idempotency {
		private long ttlHours = 24;
	}

	/**
	 * 定时通知调度器配置。
	 */
	@Data
	public static class Scheduler {
		private long fixedDelayMs = 5000;
	}

	/**
	 * 各渠道 Provider 速率限制（TPS）。
	 */
	@Data
	public static class Provider {
		private double pushTps = 500;
		private double emailTps = 200;
		private double smsTps = 100;
	}
}
