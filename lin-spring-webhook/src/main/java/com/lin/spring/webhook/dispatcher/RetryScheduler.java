package com.lin.spring.webhook.dispatcher;

import com.lin.spring.webhook.config.WebhookProperties;
import com.lin.spring.webhook.dto.DeliveryJob;
import com.lin.spring.webhook.queue.DeliveryQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * 延迟队列调度器：把已到期的重试 / 降级任务捞回主队列。
 *
 * <p>等待发生在队列里而不是线程里，因此长时间退避（1 小时、1 天）不会占用任何投递线程。</p>
 */
@Slf4j
@Component
public class RetryScheduler {

	private final DeliveryQueue queue;
	private final WebhookProperties properties;

	public RetryScheduler(DeliveryQueue queue, WebhookProperties properties) {
		this.queue = queue;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${app.webhook.delivery.scheduler-delay-ms:1000}")
	public void promoteDueJobs() {
		try {
			List<DeliveryJob> due = queue.drainDue(Instant.now(), properties.getDelivery().getSchedulerBatchSize());
			for (DeliveryJob job : due) {
				queue.pushReady(job);
			}
			if (!due.isEmpty()) {
				log.debug("延迟队列到期 {} 条，已回主队列", due.size());
			}
		} catch (RuntimeException ex) {
			log.error("延迟队列调度失败", ex);
		}
	}
}
