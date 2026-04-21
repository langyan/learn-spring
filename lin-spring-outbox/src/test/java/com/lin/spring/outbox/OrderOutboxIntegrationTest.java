package com.lin.spring.outbox;

import com.lin.spring.outbox.dto.CreateOrderRequest;
import com.lin.spring.outbox.entity.OutboxMessage;
import com.lin.spring.outbox.entity.OutboxStatus;
import com.lin.spring.outbox.outbox.LoggingOutboxMessagePublisher;
import com.lin.spring.outbox.repository.OrderRepository;
import com.lin.spring.outbox.repository.OutboxMessageRepository;
import com.lin.spring.outbox.service.OrderService;
import com.lin.spring.outbox.service.OutboxRelayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证同事务写入与 Outbox 中继行为。
 */
@SpringBootTest
class OrderOutboxIntegrationTest {

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OutboxMessageRepository outboxMessageRepository;

	@Autowired
	private OutboxRelayService outboxRelayService;

	@Autowired
	private LoggingOutboxMessagePublisher loggingOutboxMessagePublisher;

	@BeforeEach
	void resetData() {
		outboxMessageRepository.deleteAll();
		orderRepository.deleteAll();
		loggingOutboxMessagePublisher.clearPublishedForTest();
	}

	@Test
	void createOrderThenFail_shouldRollbackBothOrderAndOutbox() {
		CreateOrderRequest req = new CreateOrderRequest();
		req.setCustomerName("Bob");
		req.setAmount(BigDecimal.TEN);

		assertThatThrownBy(() -> orderService.createOrderThenFailForTest(req))
			.isInstanceOf(IllegalStateException.class);

		assertThat(orderRepository.count()).isZero();
		assertThat(outboxMessageRepository.count()).isZero();
	}

	@Test
	void createOrder_thenRelay_shouldMarkPublishedAndDeliver() {
		CreateOrderRequest req = new CreateOrderRequest();
		req.setCustomerName("Carol");
		req.setAmount(new BigDecimal("42.50"));

		var order = orderService.createOrder(req);
		assertThat(order.getId()).isNotNull();

		assertThat(outboxMessageRepository.countByStatus(OutboxStatus.PENDING)).isEqualTo(1);

		outboxRelayService.processPendingBatch();

		OutboxMessage row = outboxMessageRepository.findAll().get(0);
		assertThat(row.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
		assertThat(row.getProcessedAt()).isNotNull();

		assertThat(loggingOutboxMessagePublisher.getPublishedSnapshot()).hasSize(1);
		assertThat(loggingOutboxMessagePublisher.getPublishedSnapshot().get(0).eventType()).isEqualTo("OrderPlaced");
	}
}
