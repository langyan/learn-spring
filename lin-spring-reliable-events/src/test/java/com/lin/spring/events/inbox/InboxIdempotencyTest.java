package com.lin.spring.events.inbox;

import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.ops.FailureSimulator;
import com.lin.spring.events.order.OrderService;
import com.lin.spring.events.order.dto.CreateOrderRequest;
import com.lin.spring.events.order.repository.OrderRepository;
import com.lin.spring.events.order.repository.ProcessedOrderRepository;
import com.lin.spring.events.outbox.OutboxDispatcherScheduler;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inbox 幂等消费测试：重复投递判 DUPLICATE，下游副作用只发生一次。
 */
@SpringBootTest
class InboxIdempotencyTest {

	@Autowired
	private OrderService orderService;

	@Autowired
	private OutboxDispatcherScheduler dispatcherScheduler;

	@Autowired
	private InboxService inboxService;

	@Autowired
	private InMemoryEventBus eventBus;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private InboxEventRepository inboxEventRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ProcessedOrderRepository processedOrderRepository;

	@Autowired
	private FailureSimulator simulator;

	@BeforeEach
	void clean() {
		simulator.reset();
		eventBus.clearDeliveredForTest();
		orderRepository.deleteAll();
		outboxEventRepository.deleteAll();
		inboxEventRepository.deleteAll();
		processedOrderRepository.deleteAll();
	}

	@Test
	@DisplayName("同一 eventId 重投 → DUPLICATE，投影仍然只有一条")
	void duplicateDeliveryIsDeduplicated() {
		orderService.createOrder(request());
		dispatcherScheduler.dispatchOnce();

		assertThat(processedOrderRepository.count()).isEqualTo(1);
		var inboxEvent = inboxEventRepository.findAll().get(0);
		assertThat(inboxEvent.getStatus()).isEqualTo(InboxStatus.PROCESSED);

		// 原样重投两次（模拟 at-least-once 重复投递）
		InboxService.ReceiveResult first = inboxService.receive(inboxEvent.getEventId(),
			inboxEvent.getEventType(), inboxEvent.getSource(), inboxEvent.getCorrelationId(),
			inboxEvent.getPayload());
		InboxService.ReceiveResult second = inboxService.receive(inboxEvent.getEventId(),
			inboxEvent.getEventType(), inboxEvent.getSource(), inboxEvent.getCorrelationId(),
			inboxEvent.getPayload());

		assertThat(first).isEqualTo(InboxService.ReceiveResult.DUPLICATE);
		assertThat(second).isEqualTo(InboxService.ReceiveResult.DUPLICATE);
		assertThat(processedOrderRepository.count()).isEqualTo(1);
		assertThat(inboxEventRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("注入重复投递 → 总送达 3 次仍只处理一次（幂等边界）")
	void simulatedAtLeastOnceRedeliveryHandled() {
		simulator.setDuplicateNext(true);
		orderService.createOrder(request());
		dispatcherScheduler.dispatchOnce();

		// 总共送达 3 次（1 次正常 + 2 次重复）
		assertThat(eventBus.getDeliveredSnapshot()).hasSize(3);
		assertThat(inboxEventRepository.count()).isEqualTo(1);
		assertThat(inboxEventRepository.findAll().get(0).getStatus()).isEqualTo(InboxStatus.PROCESSED);
		assertThat(processedOrderRepository.count()).isEqualTo(1);
	}

	private CreateOrderRequest request() {
		CreateOrderRequest r = new CreateOrderRequest();
		r.setCustomerName("Carol");
		r.setAmount(new BigDecimal("299.50"));
		return r;
	}
}
