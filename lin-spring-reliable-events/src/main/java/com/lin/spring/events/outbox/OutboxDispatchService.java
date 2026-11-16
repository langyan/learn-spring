package com.lin.spring.events.outbox;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.observability.EventsMetrics;
import com.lin.spring.events.ops.FailureSimulator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Outbox 派发编排：claim（事务提交）→ publish（事务外 IO）→ mark（新事务回写）。
 * <p>
 * 发布慢或失败不会占用数据库连接；每条独立处理，单条失败不影响整批。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxDispatchService {

	private final OutboxClaimService claimService;

	private final OutboxMarkService markService;

	private final EventPublisher eventPublisher;

	private final com.lin.spring.events.outbox.repository.OutboxEventRepository outboxEventRepository;

	private final EventsProperties properties;

	private final FailureSimulator failureSimulator;

	private final EventsMetrics metrics;

	/**
	 * 手动重放 FAILED 事件：重置 PENDING 立即派发（运维接口与测试入口）。
	 *
	 * @return true 表示重置成功
	 */
	@org.springframework.transaction.annotation.Transactional
	public boolean replayFailed(Long id) {
		return outboxEventRepository.replay(id, java.time.Instant.now()) > 0;
	}

	/**
	 * 派发一批到期事件（供调度器与测试调用）。
	 *
	 * @return 本批尝试处理的条数
	 */
	public int dispatchBatch() {
		List<com.lin.spring.events.outbox.entity.OutboxEvent> claimed =
			claimService.claimBatch(properties.getInstanceId(), properties.getOutbox().getBatchSize());
		if (claimed.isEmpty()) {
			return 0;
		}

		// 故障演练：模拟「申领成功后 Pod 崩溃」——不发布也不回写，记录停留在 PROCESSING
		if (failureSimulator.shouldCrashAfterClaim()) {
			log.error("SIMULATED CRASH after claim: {} event(s) stuck in PROCESSING (owner={})",

				claimed.size(), properties.getInstanceId());
			return claimed.size();
		}

		for (com.lin.spring.events.outbox.entity.OutboxEvent event : claimed) {
			dispatchOne(event);
		}
		return claimed.size();
	}

	/**
	 * 处理单条：故障注入检查 → 发布（计时）→ 成功/失败回写。
	 */
	private void dispatchOne(com.lin.spring.events.outbox.entity.OutboxEvent event) {
		try {
			failureSimulator.maybeFailPublish(event.getEventId());
			long start = System.nanoTime();
			eventPublisher.publish(event);
			metrics.publishDuration(System.nanoTime() - start);
			markService.markPublished(event);
			metrics.outboxPublished();
			log.debug("Outbox published: id={} eventId={}", event.getId(), event.getEventId());
		} catch (Exception e) {
			metrics.outboxFailed();
			boolean exhausted = markService.markFailed(event, e);
			if (!exhausted) {
				log.warn("Outbox publish failed: id={} eventId={} attempts={} err={}",
					event.getId(), event.getEventId(), event.getAttempts(), e.getMessage());
			}
		}
	}
}
