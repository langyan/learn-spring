package com.lin.spring.events.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 事件基础设施指标封装：统一注册与命名（events.*），避免散落各处的样板代码。
 * <ul>
 *   <li>events.outbox.published / events.outbox.failed：投递成功/失败计数</li>
 *   <li>events.inbox.processed / events.inbox.duplicates / events.inbox.failed：处理/去重/失败计数</li>
 *   <li>events.outbox.backlog / events.inbox.backlog：积压深度 Gauge</li>
 *   <li>events.outbox.publish.duration：投递耗时 Timer</li>
 * </ul>
 */
@Component
public class EventsMetrics {

	private final MeterRegistry registry;

	private final Counter outboxPublished;
	private final Counter outboxFailed;
	private final Counter inboxProcessed;
	private final Counter inboxDuplicates;
	private final Counter inboxFailed;
	private final Counter staleLocksRecovered;
	private final Timer publishDuration;

	public EventsMetrics(MeterRegistry registry) {
		this.registry = registry;
		this.outboxPublished = Counter.builder("events_outbox_published_total")
			.description("Outbox 事件成功投递数")
			.register(registry);
		this.outboxFailed = Counter.builder("events_outbox_failed_total")
			.description("Outbox 事件投递失败数")
			.register(registry);
		this.inboxProcessed = Counter.builder("events_inbox_processed_total")
			.description("Inbox 事件成功处理数")
			.register(registry);
		this.inboxDuplicates = Counter.builder("events_inbox_duplicates_total")
			.description("Inbox 判定重复（DUPLICATE）的事件数")
			.register(registry);
		this.inboxFailed = Counter.builder("events_inbox_failed_total")
			.description("Inbox 事件处理失败数")
			.register(registry);
		this.staleLocksRecovered = Counter.builder("events_stale_locks_recovered_total")
			.description("陈旧锁回收条数")
			.register(registry);
		this.publishDuration = Timer.builder("events_outbox_publish_duration")
			.description("Outbox 单条投递耗时")
			.register(registry);
	}

	public void outboxPublished() {
		outboxPublished.increment();
	}

	public void outboxFailed() {
		outboxFailed.increment();
	}

	public void inboxProcessed() {
		inboxProcessed.increment();
	}

	public void inboxDuplicate() {
		inboxDuplicates.increment();
	}

	public void inboxFailed() {
		inboxFailed.increment();
	}

	public void staleLocksRecovered(double n) {
		staleLocksRecovered.increment(n);
	}

	/**
	 * 记录一次投递耗时。
	 */
	public void publishDuration(long nanos) {
		publishDuration.record(nanos, TimeUnit.NANOSECONDS);
	}

	/**
	 * 注册积压深度 Gauge（供给方为实时查询）。
	 */
	public void registerBacklogGauge(String name, Supplier<Number> supplier) {
		Gauge.builder("events_" + name + "_backlog", supplier)
			.description(name + " 待处理积压深度")
			.register(registry);
	}

	/**
	 * 注册调度器状态 Gauge（FAILED=1 / 其他=0），供告警。
	 */
	public void registerSchedulerGauge(String name, Supplier<Number> supplier) {
		Gauge.builder("events_scheduler_" + name + "_failed", supplier)
			.description(name + " 调度器是否 FAILED（1=是）")
			.register(registry);
	}
}
