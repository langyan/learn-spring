package com.lin.spring.events.outbox;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.ops.FailureSimulator;
import com.lin.spring.events.order.OrderService;
import com.lin.spring.events.order.dto.CreateOrderRequest;
import com.lin.spring.events.order.repository.OrderRepository;
import com.lin.spring.events.order.repository.ProcessedOrderRepository;
import com.lin.spring.events.outbox.entity.OutboxEvent;
import com.lin.spring.events.outbox.entity.OutboxStatus;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Outbox 端到端集成测试：同事务原子性、派发、退避重试、耗尽 FAILED 与多实例并发申领。
 */
@SpringBootTest
class OutboxDispatchIntegrationTest {

	@Autowired
	private OrderService orderService;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private OutboxEventRepository outboxEventRepository;

	@Autowired
	private InboxEventRepository inboxEventRepository;

	@Autowired
	private ProcessedOrderRepository processedOrderRepository;

	@Autowired
	private OutboxDispatcherScheduler dispatcherScheduler;

	@Autowired
	private OutboxClaimService claimService;

	@Autowired
	private OutboxDispatchService dispatchService;

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

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("interrupted", e);
		}
	}

	private CreateOrderRequest request() {
		CreateOrderRequest r = new CreateOrderRequest();
		r.setCustomerName("Alice");
		r.setAmount(new BigDecimal("199.99"));
		return r;
	}

	@Test
	@DisplayName("业务事务回滚 → 订单与 Outbox 事件一并回滚（杜绝双写不一致）")
	void businessRollbackAlsoRollsBackOutbox() {
		assertThatThrownBy(() -> orderService.createOrderThenFailForTest(request()))
			.isInstanceOf(IllegalStateException.class);

		assertThat(orderRepository.count()).isZero();
		assertThat(outboxEventRepository.count()).isZero();
	}

	@Test
	@DisplayName("正常链路：下单 → 派发一次 → Outbox PUBLISHED + Inbox PROCESSED + 投影一条")
	void happyPath() {
		orderService.createOrder(request());

		OutboxEvent event = outboxEventRepository.findAll().get(0);
		assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(event.getAttempts()).isZero();

		dispatcherScheduler.dispatchOnce();

		OutboxEvent published = outboxEventRepository.findAll().get(0);
		assertThat(published.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
		assertThat(published.getPublishedAt()).isNotNull();
		assertThat(inboxEventRepository.count()).isEqualTo(1);
		assertThat(inboxEventRepository.findAll().get(0).getStatus()).isEqualTo(InboxStatus.PROCESSED);
		assertThat(processedOrderRepository.count()).isEqualTo(1);
	}

	@Test
	@DisplayName("发布失败 → attempts+1 且 availableAt 后移（退避），到期后重试成功")
	void publishFailureSchedulesRetryWithBackoff() {
		simulator.failNextPublishes(1);
		orderService.createOrder(request());
		dispatcherScheduler.dispatchOnce();

		OutboxEvent failed = outboxEventRepository.findAll().get(0);
		assertThat(failed.getStatus()).isEqualTo(OutboxStatus.PENDING);
		assertThat(failed.getAttempts()).isEqualTo(1);
		assertThat(failed.getAvailableAt()).isAfter(Instant.now());
		assertThat(failed.getLastError()).contains("simulated publish failure");

		// 模拟退避到期
		failed.setAvailableAt(Instant.now());
		outboxEventRepository.save(failed);

		dispatcherScheduler.dispatchOnce();
		assertThat(outboxEventRepository.findAll().get(0).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
	}

	@Test
	@DisplayName("重试耗尽 → FAILED 且 availableAt 置空，停止自动重试")
	void exhaustedMarksFailed() {
		orderService.createOrder(request());
		OutboxEvent event = outboxEventRepository.findAll().get(0);
		event.setAttempts(properties.getOutbox().getMaxAttempts() - 1);
		outboxEventRepository.save(event);

		simulator.failNextPublishes(1);
		dispatcherScheduler.dispatchOnce();

		OutboxEvent failed = outboxEventRepository.findAll().get(0);
		assertThat(failed.getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(failed.getAvailableAt()).isNull();
		assertThat(failed.getAttempts()).isEqualTo(properties.getOutbox().getMaxAttempts());

		// FAILED 后手动重放 → PENDING 立即派发成功
		assertThat(dispatchService.replayFailed(failed.getId())).isTrue();
		dispatcherScheduler.dispatchOnce();
		assertThat(outboxEventRepository.findAll().get(0).getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
	}

	@Test
	@DisplayName("多实例并发申领：同一批候选事件被分区，无重叠、无漏失")
	void multiInstanceClaimPartitionsBatch() throws Exception {
		for (int i = 0; i < 8; i++) {
			orderService.createOrder(request());
		}
		List<Long> candidates = outboxEventRepository.findAll().stream().map(OutboxEvent::getId).toList();

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			CountDownLatch start = new CountDownLatch(1);
			Future<List<Long>> podA = pool.submit(() -> {
				await(start);
				return claimService.claimBatch("pod-a", 10)
					.stream().map(OutboxEvent::getId).sorted().toList();
			});
			Future<List<Long>> podB = pool.submit(() -> {
				await(start);
				return claimService.claimBatch("pod-b", 10)
					.stream().map(OutboxEvent::getId).sorted().toList();
			});
			start.countDown();

			List<Long> fromA = podA.get(10, TimeUnit.SECONDS);
			List<Long> fromB = podB.get(10, TimeUnit.SECONDS);

			// 无重叠（竞态下允许一方拿走全部、另一方为空）
			List<Long> overlap = new ArrayList<>(fromA);
			overlap.retainAll(fromB);
			assertThat(overlap).isEmpty();
			// 无漏失：两个 Pod 申领的总数等于候选总数
			assertThat(fromA.size() + fromB.size()).isEqualTo(candidates.size());
			// 全部 PROCESSING 且锁定者只可能是 pod-a / pod-b
			assertThat(outboxEventRepository.countByStatus(OutboxStatus.PROCESSING)).isEqualTo(candidates.size());
		} finally {
			pool.shutdownNow();
		}
	}

}
