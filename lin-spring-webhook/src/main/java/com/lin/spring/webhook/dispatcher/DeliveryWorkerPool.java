package com.lin.spring.webhook.dispatcher;

import com.lin.spring.webhook.config.WebhookProperties;
import com.lin.spring.webhook.dto.DeliveryJob;
import com.lin.spring.webhook.dto.DeliveryOutcome;
import com.lin.spring.webhook.queue.DeliveryQueue;
import com.lin.spring.webhook.service.DeliveryOrchestrator;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 投递 worker 池。
 *
 * <p>这正是面试追问 2 里会被慢商户打满的那个固定线程池。本模块靠「每商户并发配额 +
 * 每目的地断路器」保证它不会被单一商户耗尽。</p>
 */
@Slf4j
@Component
public class DeliveryWorkerPool {

	private final DeliveryQueue queue;
	private final DeliveryOrchestrator orchestrator;
	private final Executor executor;
	private final WebhookProperties properties;
	private final AtomicBoolean running = new AtomicBoolean(false);

	public DeliveryWorkerPool(
		@Qualifier("webhookWorkerExecutor") Executor executor,
		DeliveryOrchestrator orchestrator,
		WebhookProperties properties,
		DeliveryQueue queue
	) {
		this.executor = executor;
		this.orchestrator = orchestrator;
		this.properties = properties;
		this.queue = queue;
	}

	@EventListener(ApplicationReadyEvent.class)
	public void start() {
		int threads = properties.getDelivery().getWorkerThreads();
		if (threads <= 0) {
			log.info("投递 worker 线程数为 0，不启动 worker（测试场景）");
			return;
		}
		running.set(true);
		for (int i = 0; i < threads; i++) {
			executor.execute(this::loop);
		}
		log.info("投递 worker 池已启动，线程数={}", threads);
	}

	@PreDestroy
	public void stop() {
		running.set(false);
	}

	private void loop() {
		Duration timeout = Duration.ofSeconds(1);
		while (running.get()) {
			try {
				List<DeliveryJob> jobs = queue.popReady(properties.getDelivery().getBatchSize(), timeout);
				for (DeliveryJob job : jobs) {
					DeliveryOutcome outcome = orchestrator.deliver(job);
					log.debug("投递完成 eventId={} attempt={} outcome={}",
						job.eventId(), job.attempt(), outcome.kind());
				}
			} catch (RuntimeException ex) {
				log.error("投递循环异常", ex);
			}
		}
	}
}
