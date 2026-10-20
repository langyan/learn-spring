package com.lin.spring.webhook.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * 本模块全部可调参数（app.webhook.*）。
 */
@Data
@ConfigurationProperties(prefix = "app.webhook")
public class WebhookProperties {

	private Topics topics = new Topics();
	private Retry retry = new Retry();
	private Delivery delivery = new Delivery();
	private Isolation isolation = new Isolation();
	private Circuit circuit = new Circuit();
	private Ssrf ssrf = new Ssrf();
	private Idempotency idempotency = new Idempotency();

	@Data
	public static class Topics {
		/** 事件接入 topic */
		private String events = "webhook.events";
		/** 死信 topic */
		private String dlq = "webhook.dlq";
	}

	@Data
	public static class Retry {
		/** 指数退避序列（秒）：1 分钟 / 5 分钟 / 1 小时 ... */
		private List<Long> backoffSeconds = List.of(60L, 300L, 3600L, 10800L, 21600L, 43200L, 86400L);
		/** 最大尝试次数 */
		private int maxAttempts = 8;
		/** 最长重试窗口（天），对齐面试描述的三天 */
		private long maxDurationDays = 3;
		/** 抖动比例，避免同一商户重试同时到期造成惊群 */
		private double jitterRatio = 0.2;
	}

	@Data
	public static class Delivery {
		private long connectTimeoutMs = 2000;
		private long responseTimeoutMs = 5000;
		private int maxConnections = 200;
		private int maxConnectionsPerRoute = 20;
		/** 投递 worker 线程数（固定池，正是噪声邻居会打满的资源） */
		private int workerThreads = 8;
		private int batchSize = 32;
		/** 延迟队列调度器的轮询间隔（毫秒） */
		private long schedulerDelayMs = 1000;
		/** 调度器单次捞取条数 */
		private int schedulerBatchSize = 128;
	}

	@Data
	public static class Isolation {
		/** 每个商户同时占用的投递线程上限 */
		private int perMerchantMaxConcurrency = 3;
		/** 配额耗尽时回退到延迟队列的等待秒数 */
		private long deferSeconds = 5;
	}

	@Data
	public static class Circuit {
		private float failureRateThreshold = 50f;
		private int slidingWindowSize = 20;
		private int minimumNumberOfCalls = 5;
		private long waitDurationInOpenStateSeconds = 30;
		private int permittedNumberOfCallsInHalfOpenState = 2;
		/** 熔断持续超过该时长视为商户彻底离线，事件直接取消 */
		private long maxOfflineMinutes = 30;
	}

	@Data
	public static class Ssrf {
		/** 端口白名单，为空表示不限制端口（生产环境建议只放开 80/443） */
		private List<Integer> allowedPorts = List.of();
		/** 主机白名单，命中后跳过公网 IP 校验（仅用于本地演示放行 mock 接收端） */
		private List<String> allowedHosts = List.of();
		/** 已校验 IP 的 pin 有效期（秒） */
		private long pinTtlSeconds = 300;
		private boolean blockRedirects = true;
	}

	@Data
	public static class Idempotency {
		private long ttlHours = 72;
	}
}
