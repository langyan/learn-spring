package com.lin.spring.events.inbox;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.ops.FailureSimulator;
import com.lin.spring.events.order.OrderService;
import com.lin.spring.events.order.dto.CreateOrderRequest;
import com.lin.spring.events.order.repository.OrderRepository;
import com.lin.spring.events.order.repository.ProcessedOrderRepository;
import com.lin.spring.events.outbox.OutboxDispatcherScheduler;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import com.lin.spring.events.retry.InboxRetryScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inbox 持久重试测试：处理失败 → 退避重试 → 耗尽 FAILED → 手动重放成功。
 * 重试状态全部落库（availableAt/attempts/lastError），重启后可继续。
 */
@SpringBootTest
class InboxRetryTest {

	@Autowired
	private OrderService orderService;

	@Autowired
	private OutboxDispatcherScheduler dispatcherScheduler;

	@Autowired
	private InboxRetryScheduler retryScheduler;

	@Autowired
	private InboxService inboxService;

	@Autowired
	private InboxEventRepository inboxEventRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ProcessedOrderRepository processedOrderRepository;

	@Autowired
	private FailureSimulator simulator;

	@Autowired
	private EventsProperties properties;

	@BeforeEach
	void clean() {
		simulator.reset();
		orderRepository.deleteAll();
		outboxEventRepository.deleteAll();
		inboxEventRepository.deleteAll();
		processedOrderRepository.deleteAll();
	}

	@Test
	@DisplayName("处理失败 → RECEIVED + availableAt 退避后移，到期重试成功")
	void handlerFailureRetriedWithBackoff() {
		simulator.failNextHandlers(1);
		orderService.createOrder(request());
		dispatcherScheduler.dispatchOnce();

		var event = inboxEventRepository.findAll().get(0);
		assertThat(event.getStatus()).isEqualTo(InboxStatus.RECEIVED);
		assertThat(event.getAttempts()).isEqualTo(1);
		assertThat(event.getAvailableAt()).isAfter(Instant.now());
		assertThat(event.getLastError()).contains("simulated handler failure");
		assertThat(processedOrderRepository.count()).isZero();

		// 未到期：重试器空转
		assertThat(retryScheduler.retryOnce()).isZero();

		// 模拟退避到期 → 重试成功
		event.setAvailableAt(Instant.now());
		inboxEventRepository.save(event);
		assertThat(retryScheduler.retryOnce()).isEqualTo(1);

		var processed = inboxEventRepository.findAll().get(0);
		assertThat(processed.getStatus()).isEqualTo(InboxStatus.PROCESSED);
		assertThat(processed.getAttempts()).isEqualTo(2);
		assertThat(processedOrderRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("重试耗尽 → FAILED 且 availableAt 置空；手动重放后处理成功")
	void exhaustedThenManualReplay() {
		orderService.createOrder(request());
		dispatcherScheduler.dispatchOnce();

		// 构造「重试中、只剩最后一次尝试」的现场（状态回 RECEIVED 才会被重试器申领）
		var event = inboxEventRepository.findAll().get(0);
		event.setStatus(com.lin.spring.events.inbox.entity.InboxStatus.RECEIVED);
		event.setAttempts(properties.getInbox().getMaxAttempts() - 1);
		event.setAvailableAt(Instant.now());
		inboxEventRepository.save(event);

		simulator.failNextHandlers(1);
		retryScheduler.retryOnce();

		var failed = inboxEventRepository.findAll().get(0);
		assertThat(failed.getStatus()).isEqualTo(InboxStatus.FAILED);
		assertThat(failed.getAvailableAt()).isNull();
		assertThat(failed.getAttempts()).isEqualTo(properties.getInbox().getMaxAttempts());

		// FAILED 不会被重试器扫到
		assertThat(retryScheduler.retryOnce()).isZero();

		// 手动重放 → 重置 RECEIVED/attempts → 立即处理成功
		assertThat(inboxService.replay(failed.getEventId())).isTrue();
		retryScheduler.retryOnce();

		var recovered = inboxEventRepository.findAll().get(0);
		assertThat(recovered.getStatus()).isEqualTo(InboxStatus.PROCESSED);
		assertThat(processedOrderRepository.count()).isEqualTo(1);
	}

	private CreateOrderRequest request() {
		CreateOrderRequest r = new CreateOrderRequest();
		r.setCustomerName("Dave");
		r.setAmount(new BigDecimal("88.88"));
		return r;
	}
}
