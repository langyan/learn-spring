package com.lin.spring.webhook.queue;

import com.lin.spring.webhook.dto.DeliveryJob;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 投递队列抽象：主队列（就绪）+ 延迟队列（退避重试 / 熔断降级）。
 *
 * <p>延迟队列是 Redis ZSET（score = 下次投递时间），由调度器到期捞回主队列。
 * 相比「worker 里 Thread.sleep 等待退避」，等待期间不占用任何投递线程。</p>
 */
public interface DeliveryQueue {

	/** 任务进入主队列尾部（重试任务就是这样回到队尾，不阻塞新事件）。 */
	void pushReady(DeliveryJob job);

	/** 任务进入延迟队列，delaySeconds 秒后才可能被捞回主队列。 */
	void pushDelayed(DeliveryJob job, long delaySeconds);

	/** 阻塞地从主队列取任务。 */
	List<DeliveryJob> popReady(int maxBatch, Duration timeout);

	/** 取出所有已到期的延迟任务。 */
	List<DeliveryJob> drainDue(Instant now, int maxBatch);

	long readyDepth();

	long delayedDepth();
}
