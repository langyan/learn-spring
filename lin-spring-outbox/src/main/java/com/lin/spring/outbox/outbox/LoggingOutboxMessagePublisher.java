package com.lin.spring.outbox.outbox;

import com.lin.spring.outbox.entity.OutboxMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认实现：记录日志并将载荷副本放入内存列表，便于本地演示与测试断言。
 */
@Slf4j
@Component
@Profile("!kafka")
@Primary
public class LoggingOutboxMessagePublisher implements OutboxMessagePublisher {

	private final List<PublishedRecord> published = new CopyOnWriteArrayList<>();

	@Override
	public void publish(OutboxMessage message) {
		log.info("Outbox relay publish: eventId={} aggregateId={} type={} payload={}",
			message.getEventId(),
			message.getAggregateId(),
			message.getEventType(),
			message.getPayload());
		published.add(new PublishedRecord(
			message.getEventId(),
			message.getAggregateId(),
			message.getEventType(),
			message.getPayload()
		));
	}

	/**
	 * 测试或演示用：已「投递」的消息副本（线程安全）。
	 */
	public List<PublishedRecord> getPublishedSnapshot() {
		return List.copyOf(published);
	}

	/**
	 * 测试用：清空内存副本。
	 */
	public void clearPublishedForTest() {
		published.clear();
	}

	/**
	 * 一次投递的只读快照。
	 */
	public record PublishedRecord(String eventId, String aggregateId, String eventType, String payload) {
	}
}
