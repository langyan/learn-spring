package com.lin.spring.events.retry;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.config.SchedulingConfig;
import com.lin.spring.events.inbox.InboxService;
import com.lin.spring.events.observability.SchedulerLifecycle;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Inbox 重试调度器：持久化重试（非 @Retryable 内存态）。
 * <p>
 * 重试状态在数据库（status/availableAt/attempts），进程重启后到期记录自动被再次扫到，
 * 「重试必须能在应用重启后存活」。自调度 + 生命周期与派发器同构。
 * </p>
 */
@Slf4j
@Component
public class InboxRetryScheduler {

	private final InboxService inboxService;

	private final TaskScheduler taskScheduler;

	private final EventsProperties properties;

	private final AtomicBoolean running = new AtomicBoolean(false);

	private volatile ScheduledFuture<?> nextRun;

	/** 生命周期：对运维与健康检查暴露。 */
	public final SchedulerLifecycle lifecycle = new SchedulerLifecycle("inbox-retry");

	public InboxRetryScheduler(InboxService inboxService,
							   @Qualifier(SchedulingConfig.SCHEDULER_BEAN_NAME) TaskScheduler taskScheduler,
							   EventsProperties properties) {
		this.inboxService = inboxService;
		this.taskScheduler = taskScheduler;
		this.properties = properties;
	}

	@PostConstruct
	public void start() {
		if (!properties.isSchedulerAutoStart()) {
			log.info("Inbox retry scheduler auto-start disabled");
			return;
		}
		lifecycle.starting();
		scheduleNext(Instant.now());
		log.info("Inbox retry scheduler started: intervalMs={}", properties.getInbox().getPollIntervalMs());
	}

	void tick() {
		if (!running.compareAndSet(false, true)) {
			return;
		}
		lifecycle.tickStarted();
		try {
			int processed = inboxService.processDueEvents();
			if (processed > 0) {
				log.debug("Inbox retry tick processed {} event(s)", processed);
			}
		} catch (Exception e) {
			log.error("Inbox retry tick error", e);
		} finally {
			scheduleNext(Instant.now().plus(Duration.ofMillis(properties.getInbox().getPollIntervalMs())));
			running.set(false);
			lifecycle.tickFinished();
		}
	}

	private void scheduleNext(Instant time) {
		try {
			nextRun = taskScheduler.schedule(this::tick, time);
			lifecycle.nextScheduled();
		} catch (TaskRejectedException | IllegalStateException e) {
			lifecycle.nextScheduleRejected();
			lifecycle.failed("schedule next run rejected: " + e.getMessage());
			log.error("Inbox retry scheduler FAILED to schedule next run: {}", e.getMessage());
		}
	}

	/**
	 * 手动触发一次（测试与运维接口用）。
	 */
	public int retryOnce() {
		return inboxService.processDueEvents();
	}

	@PreDestroy
	public void stop() {
		ScheduledFuture<?> future = nextRun;
		if (future != null) {
			future.cancel(false);
		}
		log.info("Inbox retry scheduler stopped");
	}
}
