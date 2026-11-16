package com.lin.spring.events.outbox;

import com.lin.spring.events.inbox.entity.InboxEvent;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.order.OrderService;
import com.lin.spring.events.order.dto.CreateOrderRequest;
import com.lin.spring.events.order.repository.OrderRepository;
import com.lin.spring.events.order.repository.ProcessedOrderRepository;
import com.lin.spring.events.outbox.entity.OutboxEvent;
import com.lin.spring.events.outbox.entity.OutboxStatus;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import com.lin.spring.events.retry.StaleLockRecoveryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 陈旧锁回收测试：Pod 申领后「崩溃」（不回写状态），超过 lockTimeout 后被回收并重新投递。
 */
@SpringBootTest
class StaleLockRecoveryTest {

	@Autowired
	private OrderService orderService;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private InboxEventRepository inboxEventRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private ProcessedOrderRepository processedOrderRepository;

	@Autowired
	private OutboxClaimService claimService;

	@Autowired
	private OutboxDispatcherScheduler dispatcherScheduler;

	@Autowired
	private StaleLockRecoveryService recoveryService;

	@BeforeEach
	void clean() {
		orderRepository.deleteAll();
		outboxEventRepository.deleteAll();
		inboxEventRepository.deleteAll();
		processedOrderRepository.deleteAll();
	}

	@Test
	@DisplayName("Outbox 申领后崩溃 → 超时回收 → 重新派发成功")
	void staleOutboxLockRecoveredAndRedispatched() {
		orderService.createOrder(request());

		// Pod-A 申领后崩溃：不执行 publish、不回写状态
		var claimed = claimService.claimBatch("pod-a", 10);
		assertThat(claimed).hasSize(1);
		assertThat(outboxEventRepository.findAll().get(0).getStatus()).isEqualTo(OutboxStatus.PROCESSING);

		// 未超时：回收无效
		assertThat(recoveryService.recoverNow()).isZero();

		// 模拟锁定时间已超时
		OutboxEvent stale = outboxEventRepository.findAll().get(0);
		stale.setLockedAt(Instant.now().minusMillis(60_000));
		outboxEventRepository.save(stale);

		int recovered = recoveryService.recoverNow();
		assertThat(recovered).isEqualTo(1);

		OutboxEvent pending = outboxEventRepository.findAll().get(0);
		assertThat(pending.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(pending.getLockOwner()).isNull();

		// 重新派发：最终 PUBLISHED 且下游只处理一次
		dispatcherScheduler.dispatchOnce();
		assertThat(outboxEventRepository.findAll().get(0).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
		assertThat(processedOrderRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("Inbox 申领后崩溃 → 超时回收 → 重试器接手处理")
	void staleInboxLockRecoveredAndProcessed() {
		InboxEvent stale = new InboxEvent("evt-stale-1", "OrderPlaced", "order-service", "corr-1",
			"""
			{"eventId":"evt-stale-1","eventType":"OrderPlaced","correlationId":"corr-1",\
			"payload":{"orderId":42,"customerName":"Bob","amount":59.00,"correlationId":"corr-1"}}
			""");
		stale.setStatus(InboxStatus.PROCESSING);
		stale.setAttempts(1);
		stale.setLockOwner("pod-a");
		stale.setLockedAt(Instant.now().minusMillis(60_000));
		inboxEventRepository.save(stale);

		int recovered = recoveryService.recoverNow();
		assertThat(recovered).isEqualTo(1);

		InboxEvent pending = inboxEventRepository.findAll().get(0);
		assertThat(pending.getStatus()).isEqualTo(InboxStatus.RECEIVED);
		assertThat(pending.getLockOwner()).isNull();

		// 到期（availableAt 未变，立即）→ 重试器处理成功
		retryScheduler.retryOnce();
		assertThat(inboxEventRepository.findAll().get(0).getStatus()).isEqualTo(InboxStatus.PROCESSED);
		assertThat(processedOrderRepository.count()).isEqualTo(1);
	}

	@Autowired
	private com.lin.spring.events.retry.InboxRetryScheduler retryScheduler;

	private CreateOrderRequest request() {
		CreateOrderRequest r = new CreateOrderRequest();
		r.setCustomerName("Bob");
		r.setAmount(new BigDecimal("59.00"));
		return r;
	}
}
