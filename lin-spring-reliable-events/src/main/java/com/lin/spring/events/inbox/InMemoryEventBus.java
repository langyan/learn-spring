package com.lin.spring.events.inbox;

import com.lin.spring.events.ops.FailureSimulator;
import com.lin.spring.events.outbox.EventPublisher;
import com.lin.spring.events.outbox.entity.OutboxEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 默认通道（!kafka）：日志 + 回灌同进程 Inbox，构成零依赖闭环演示。
 * <p>
 * 保留投递快照供测试断言；支持注入「重复投递」模拟 at-least-once 行为，
 * 由 Inbox 幂等保证副作用只发生一次。
 * </p>
 */
@Slf4j
@Component
@Profile("!kafka")
@Primary
public class InMemoryEventBus implements EventPublisher {

	private final InboxService inboxService;

	private final FailureSimulator failureSimulator;

	private final List<DeliveredEvent> delivered = new CopyOnWriteArrayList<>();

	public InMemoryEventBus(InboxService inboxService, FailureSimulator failureSimulator) {
		this.inboxService = inboxService;
		this.failureSimulator = failureSimulator;
	}

	@Override
	public void publish(OutboxEvent event) {
		log.info("InMemory bus delivering: eventId={} type={} payload={}",
			event.getEventId(), event.getEventType(), event.getPayload());
		deliver(event);
		// at-least-once 演示：broker 完全可能把同一条消息再投两次（消费者 ack 前崩溃等）
		if (failureSimulator.shouldDuplicateNext()) {
			log.info("SIMULATED at-least-once redelivery x2: eventId={}", event.getEventId());
			deliver(event);
			deliver(event);
		}
	}

	private void deliver(OutboxEvent event) {
		delivered.add(new DeliveredEvent(event.getEventId(), event.getEventType(), event.getPayload()));
		inboxService.receive(event.getEventId(), event.getEventType(), event.getSource(),
			event.getCorrelationId(), event.getPayload());
	}

	/**
	 * 测试/演示：已投递消息快照（线程安全）。
	 */
	public List<DeliveredEvent> getDeliveredSnapshot() {
		return List.copyOf(delivered);
	}

	/**
	 * 测试用：清空快照。
	 */
	public void clearDeliveredForTest() {
		delivered.clear();
	}

	/**
	 * 一次投递的只读快照。
	 */
	public record DeliveredEvent(String eventId, String eventType, String payload) {
	}
}
