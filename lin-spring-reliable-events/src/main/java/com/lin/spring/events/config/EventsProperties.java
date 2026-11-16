package com.lin.spring.events.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 事件基础设施配置（app.events.*）。
 * <p>
 * outbox 与 inbox 各自独立一套批次、轮询、退避与锁参数，便于分别调优。
 * </p>
 */
@Data
@ConfigurationProperties(prefix = "app.events")
public class EventsProperties {

	/** 实例标识：抢锁时写入 lock_owner，用于多实例/Pod 区分。 */
	private String instanceId = "local-1";

	/** 调度器是否随应用自动启动（测试中通常关闭，改为手动触发）。 */
	private boolean schedulerAutoStart = true;

	private final Outbox outbox = new Outbox();

	private final Inbox inbox = new Inbox();

	private final Kafka kafka = new Kafka();

	private final Health health = new Health();

	@Data
	public static class Outbox {
		/** 每批最多申领条数。 */
		private int batchSize = 20;
		/** 派发器轮询间隔（毫秒）。 */
		private long pollIntervalMs = 2000;
		/** 最大尝试次数，超出后标记 FAILED 且不再自动重试。 */
		private int maxAttempts = 5;
		/** 指数退避基础延迟（毫秒）：第 n 次失败后延迟 base * 2^(n-1)。 */
		private long baseDelayMs = 1000;
		/** 单次退避上限（毫秒）。 */
		private long maxDelayMs = 60_000;
		/** 抖动比例：实际延迟 = 计算值 * (1 ± ratio)，避免惊群。 */
		private double jitterRatio = 0.2;
		/** PROCESSING 锁超时（毫秒）：超过视为持有者已死亡，可被回收。 */
		private long lockTimeoutMs = 60_000;
		/** 已完结（PUBLISHED）记录保留天数。 */
		private int retentionDays = 7;
		/** 单次清理每批删除条数。 */
		private int cleanupBatchSize = 200;
	}

	@Data
	public static class Inbox {
		/** 每批最多申领条数。 */
		private int batchSize = 20;
		/** 重试器轮询间隔（毫秒）。 */
		private long pollIntervalMs = 2000;
		/** 最大尝试次数，超出后标记 FAILED 等待人工重放。 */
		private int maxAttempts = 5;
		/** 指数退避基础延迟（毫秒）。 */
		private long baseDelayMs = 1000;
		/** 单次退避上限（毫秒）。 */
		private long maxDelayMs = 60_000;
		/** 抖动比例。 */
		private double jitterRatio = 0.2;
		/** PROCESSING 锁超时（毫秒）。 */
		private long lockTimeoutMs = 60_000;
		/** 已完结（PROCESSED）记录保留天数。 */
		private int retentionDays = 7;
		/** 单次清理每批删除条数。 */
		private int cleanupBatchSize = 200;
	}

	@Data
	public static class Kafka {
		/** 目标 topic。 */
		private String topic = "reliable-order-events";
		/** 发送确认等待超时（秒）。 */
		private int sendTimeoutSeconds = 5;
		/** 消费组。 */
		private String consumerGroup = "reliable-events-inbox";
	}

	@Data
	public static class Health {
		/** 待处理积压超过该值时健康状态降级。 */
		private int maxBacklog = 100;
	}
}
