package com.lin.spring.events.outbox;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.config.SchedulingConfig;
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
 * 自调度派发器：不用 @Scheduled，而是每个 tick 结尾显式提交下一次运行。
 * <p>
 * 原因：{@code @Scheduled} 的下一次执行由框架静默安排，调度被拒绝时无人知晓，
 * 调度器会继续「谎报健康」。显式提交可以捕获 {@link TaskRejectedException} 并把
 * 生命周期置为 FAILED，让「调度基础设施自身故障」可见（健康检查、指标、运维接口）。
 * </p>
 */
@Slf4j
@Component
public class OutboxDispatcherScheduler {

	private final OutboxDispatchService dispatchService;

	private final TaskScheduler taskScheduler;

	private final EventsProperties properties;

	private final AtomicBoolean running = new AtomicBoolean(false);

	private volatile ScheduledFuture<?> nextRun;

	/** 生命周期：对运维与健康检查暴露。 */
	public final SchedulerLifecycle lifecycle = new SchedulerLifecycle("outbox-dispatcher");

	public OutboxDispatcherScheduler(
		OutboxDispatchService dispatchService,
		@Qualifier(SchedulingConfig.SCHEDULER_BEAN_NAME) TaskScheduler taskScheduler,
		EventsProperties properties) {
		this.dispatchService = dispatchService;
		this.taskScheduler = taskScheduler;
		this.properties = properties;
	}

	/**
	 * 启动：提交首轮执行。
	 */
	@PostConstruct
	public void start() {
		if (!properties.isSchedulerAutoStart()) {
			log.info("Outbox dispatcher auto-start disabled");
			return;
		}
		lifecycle.starting();
		scheduleNext(Instant.now());
		log.info("Outbox dispatcher started: intervalMs={} instanceId={}",
			properties.getOutbox().getPollIntervalMs(), properties.getInstanceId());
	}

	/**
	 * 单次 tick：执行派发并自调度下一次。任何未捕获异常都记录且不让调度链断裂。
	 */
	void tick() {
		if (!running.compareAndSet(false, true)) {
			return; // 单线程池下防御性去重
		}
		lifecycle.tickStarted();
		try {
			int processed = dispatchService.dispatchBatch();
			if (processed > 0) {
				log.debug("Outbox dispatcher tick processed {} event(s)", processed);
			}
		} catch (Exception e) {
			// 派发器业务异常不影响下一次调度；调度失败才是致命的
			log.error("Outbox dispatcher tick error", e);
		} finally {
			scheduleNext(Instant.now().plus(Duration.ofMillis(properties.getOutbox().getPollIntervalMs())));
			running.set(false);
			lifecycle.tickFinished();
		}
	}

	/**
	 * 显式提交下一次执行；被拒绝则进入 FAILED。
	 */
	private void scheduleNext(Instant time) {
		try {
			nextRun = taskScheduler.schedule(this::tick, time);
			lifecycle.nextScheduled();
		} catch (TaskRejectedException | IllegalStateException e) {
			lifecycle.nextScheduleRejected();
			lifecycle.failed("schedule next run rejected: " + e.getMessage());
			log.error("Outbox dispatcher FAILED to schedule next run, no future dispatch will happen: {}", e.getMessage());
		}
	}

	/**
	 * 手动触发一次（测试与运维接口用，不影响调度链）。
	 */
	public int dispatchOnce() {
		return dispatchService.dispatchBatch();
	}

	/**
	 * 停止：取消已调度任务。
	 */
	@PreDestroy
	public void stop() {
		ScheduledFuture<?> future = nextRun;
		if (future != null) {
			future.cancel(false);
		}
		log.info("Outbox dispatcher stopped");
	}
}
