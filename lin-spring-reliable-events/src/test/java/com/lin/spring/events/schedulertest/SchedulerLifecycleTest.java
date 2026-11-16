package com.lin.spring.events.schedulertest;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.observability.SchedulerLifecycle;
import com.lin.spring.events.outbox.OutboxDispatcherScheduler;
import com.lin.spring.events.outbox.OutboxDispatchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * 调度器生命周期测试：调度被拒绝时必须转 FAILED，不得谎报 RUNNING。
 */
class SchedulerLifecycleTest {

	@Test
	@DisplayName("正常流转：STARTING → tick → RUNNING，不变量保持")
	void normalTransitions() {
		SchedulerLifecycle lifecycle = new SchedulerLifecycle("test");
		assertThat(lifecycle.state()).isEqualTo(SchedulerLifecycle.State.STOPPED);

		lifecycle.starting();
		lifecycle.tickStarted();
		assertThat(lifecycle.state()).isEqualTo(SchedulerLifecycle.State.RUNNING);

		lifecycle.nextScheduled();
		lifecycle.tickFinished();
		assertThat(lifecycle.state()).isEqualTo(SchedulerLifecycle.State.RUNNING);
		assertThat(lifecycle.healthy()).isTrue();
	}

	@Test
	@DisplayName("调度被拒绝 → FAILED 且不健康")
	void rejectLeadsToFailed() {
		TaskScheduler rejecting = mock(TaskScheduler.class);
		doThrow(new TaskRejectedException("scheduler is shutdown"))
			.when(rejecting).schedule(any(Runnable.class), any(Instant.class));

		EventsProperties properties = new EventsProperties();
		properties.setSchedulerAutoStart(true);
		OutboxDispatcherScheduler scheduler = new OutboxDispatcherScheduler(
			mock(OutboxDispatchService.class), rejecting, properties);

		scheduler.start();

		assertThat(scheduler.lifecycle.state()).isEqualTo(SchedulerLifecycle.State.FAILED);
		assertThat(scheduler.lifecycle.healthy()).isFalse();
		assertThat(scheduler.lifecycle.lastFailureReason()).contains("rejected");
	}

	@Test
	@DisplayName("tick 结束时无后续任务 → 不再报告 RUNNING（不变量）")
	void invariantNoNextScheduled() {
		SchedulerLifecycle lifecycle = new SchedulerLifecycle("test");
		lifecycle.starting();
		lifecycle.tickStarted();
		// 未调用 nextScheduled 直接结束 tick
		lifecycle.tickFinished();
		assertThat(lifecycle.state()).isNotEqualTo(SchedulerLifecycle.State.RUNNING);
	}
}
