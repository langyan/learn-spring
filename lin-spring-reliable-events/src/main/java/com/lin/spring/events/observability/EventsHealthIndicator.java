package com.lin.spring.events.observability;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.outbox.OutboxDispatcherScheduler;
import com.lin.spring.events.outbox.entity.OutboxStatus;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import com.lin.spring.events.retry.InboxRetryScheduler;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 事件基础设施健康指示器：
 * <ul>
 *   <li>任一调度器生命周期 FAILED → DOWN（调度已停止，事件将静默积压）</li>
 *   <li>积压超阈值 → OUT_OF_SERVICE（降级提示，业务仍在推进）</li>
 *   <li>同时注册积压深度与调度器失败 Gauge 供告警</li>
 * </ul>
 */
@Component("eventsInfrastructure")
public class EventsHealthIndicator implements HealthIndicator {

	private final OutboxDispatcherScheduler dispatcherScheduler;

	private final InboxRetryScheduler retryScheduler;

	private final OutboxEventRepository outboxEventRepository;

	private final InboxEventRepository inboxEventRepository;

	private final EventsProperties properties;

	private final EventsMetrics metrics;

	/** Gauge 供给方持有强引用，防止被 GC 后指标失效。 */
	@SuppressWarnings("unused")
	private final List<Supplier<Number>> gaugeSuppliers;

	public EventsHealthIndicator(OutboxDispatcherScheduler dispatcherScheduler,
								 InboxRetryScheduler retryScheduler,
								 OutboxEventRepository outboxEventRepository,
								 InboxEventRepository inboxEventRepository,
								 EventsProperties properties,
								 EventsMetrics metrics) {
		this.dispatcherScheduler = dispatcherScheduler;
		this.retryScheduler = retryScheduler;
		this.outboxEventRepository = outboxEventRepository;
		this.inboxEventRepository = inboxEventRepository;
		this.properties = properties;
		this.metrics = metrics;

		Supplier<Number> outboxBacklog = () ->
			outboxEventRepository.countByStatus(OutboxStatus.PENDING)
				+ outboxEventRepository.countByStatus(OutboxStatus.PROCESSING);
		Supplier<Number> inboxBacklog = () ->
			inboxEventRepository.countByStatus(InboxStatus.RECEIVED)
				+ inboxEventRepository.countByStatus(InboxStatus.PROCESSING);
		Supplier<Number> dispatcherFailed = () ->
			dispatcherScheduler.lifecycle.isFailed() ? 1 : 0;
		Supplier<Number> retryFailed = () ->
			retryScheduler.lifecycle.isFailed() ? 1 : 0;
		this.gaugeSuppliers = List.of(outboxBacklog, inboxBacklog, dispatcherFailed, retryFailed);

		metrics.registerBacklogGauge("outbox", outboxBacklog);
		metrics.registerBacklogGauge("inbox", inboxBacklog);
		metrics.registerSchedulerGauge("dispatcher", dispatcherFailed);
		metrics.registerSchedulerGauge("retry", retryFailed);
	}

	@Override
	public Health health() {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("outboxDispatcher", dispatcherScheduler.lifecycle.state().name());
		details.put("inboxRetry", retryScheduler.lifecycle.state().name());

		long outboxBacklog = outboxEventRepository.countByStatus(OutboxStatus.PENDING)
			+ outboxEventRepository.countByStatus(OutboxStatus.PROCESSING);
		long inboxBacklog = inboxEventRepository.countByStatus(InboxStatus.RECEIVED)
			+ inboxEventRepository.countByStatus(InboxStatus.PROCESSING);
		details.put("outboxBacklog", outboxBacklog);
		details.put("inboxBacklog", inboxBacklog);

		if (dispatcherScheduler.lifecycle.isFailed()) {
			return Health.down()
				.withDetails(details)
				.withDetail("outboxDispatcherFailure", dispatcherScheduler.lifecycle.lastFailureReason())
				.build();
		}
		if (retryScheduler.lifecycle.isFailed()) {
			return Health.down()
				.withDetails(details)
				.withDetail("inboxRetryFailure", retryScheduler.lifecycle.lastFailureReason())
				.build();
		}
		int maxBacklog = properties.getHealth().getMaxBacklog();
		if (outboxBacklog > maxBacklog || inboxBacklog > maxBacklog) {
			return Health.outOfService().withDetails(details).build();
		}
		return Health.up().withDetails(details).build();
	}
}
