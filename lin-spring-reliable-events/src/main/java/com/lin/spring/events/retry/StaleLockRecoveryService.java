package com.lin.spring.events.retry;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.observability.EventsMetrics;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * 陈旧锁回收：Pod 申领事件后崩溃（PROCESSING 停滞）时，超过 lock-timeout 的记录
 * 被重置回 PENDING，供其他实例接手——「申领后死亡」的确定性恢复机制。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaleLockRecoveryService {

	private final OutboxEventRepository outboxEventRepository;

	private final InboxEventRepository inboxEventRepository;

	private final EventsProperties properties;

	private final EventsMetrics metrics;

	/**
	 * 定期回收 Outbox / Inbox 的陈旧 PROCESSING 记录。
	 */
	@Scheduled(fixedDelayString = "${app.events.recovery.interval-ms:15000}")
	@Transactional
	public void recoverStaleLocks() {
		Instant outboxThreshold = Instant.now()
			.minus(Duration.ofMillis(properties.getOutbox().getLockTimeoutMs()));
		int outboxRecovered = outboxEventRepository.recoverStale(
			outboxThreshold, "recovered from stale lock (assumed owner crash)");
		if (outboxRecovered > 0) {
			log.warn("Recovered {} stale PROCESSING outbox event(s) back to PENDING", outboxRecovered);
		}

		Instant inboxThreshold = Instant.now()
			.minus(Duration.ofMillis(properties.getInbox().getLockTimeoutMs()));
		int inboxRecovered = inboxEventRepository.recoverStale(
			inboxThreshold, "recovered from stale lock (assumed owner crash)");
		if (inboxRecovered > 0) {
			log.warn("Recovered {} stale PROCESSING inbox event(s) back to RECEIVED", inboxRecovered);
		}

		metrics.staleLocksRecovered(outboxRecovered + inboxRecovered);
	}

	/**
	 * 手动触发（测试用）。
	 */
	@Transactional
	public int recoverNow() {
		Instant outboxThreshold = Instant.now()
			.minus(Duration.ofMillis(properties.getOutbox().getLockTimeoutMs()));
		Instant inboxThreshold = Instant.now()
			.minus(Duration.ofMillis(properties.getInbox().getLockTimeoutMs()));
		int total = outboxEventRepository.recoverStale(outboxThreshold, "manual recovery")
			+ inboxEventRepository.recoverStale(inboxThreshold, "manual recovery");
		metrics.staleLocksRecovered(total);
		return total;
	}
}
