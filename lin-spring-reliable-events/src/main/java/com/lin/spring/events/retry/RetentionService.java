package com.lin.spring.events.retry;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.outbox.entity.OutboxStatus;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * 保留清理：分批删除已完结（PUBLISHED / PROCESSED）且早于 TTL 的记录，
 * 控制热点表规模；FAILED 记录不自动删除（保留排查与重放能力）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetentionService {

	private final OutboxEventRepository outboxEventRepository;

	private final InboxEventRepository inboxEventRepository;

	private final EventsProperties properties;

	/**
	 * 定期清理（每 10 分钟）。
	 */
	@Scheduled(fixedDelayString = "${app.events.retention.interval-ms:600000}")
	public void cleanup() {
		int outboxDeleted = cleanupOutbox();
		int inboxDeleted = cleanupInbox();
		if (outboxDeleted > 0 || inboxDeleted > 0) {
			log.info("Retention cleanup: outboxDeleted={} inboxDeleted={}", outboxDeleted, inboxDeleted);
		}
	}

	/**
	 * 手动触发（运维接口与测试）。
	 */
	@Transactional
	public int cleanupNow() {
		return cleanupOutbox() + cleanupInbox();
	}

	private int cleanupOutbox() {
		Instant threshold = Instant.now()
			.minus(Duration.ofDays(properties.getOutbox().getRetentionDays()));
		int total = 0;
		int batch = properties.getOutbox().getCleanupBatchSize();
		while (true) {
			List<Long> ids = outboxEventRepository.findCleanupCandidateIds(
				OutboxStatus.PUBLISHED, threshold, PageRequest.of(0, batch));
			if (ids.isEmpty()) {
				break;
			}
			outboxEventRepository.deleteAllById(ids);
			total += ids.size();
			if (ids.size() < batch) {
				break;
			}
		}
		return total;
	}

	private int cleanupInbox() {
		Instant threshold = Instant.now()
			.minus(Duration.ofDays(properties.getInbox().getRetentionDays()));
		int total = 0;
		int batch = properties.getInbox().getCleanupBatchSize();
		while (true) {
			List<Long> ids = inboxEventRepository.findCleanupCandidateIds(
				InboxStatus.PROCESSED, threshold, PageRequest.of(0, batch));
			if (ids.isEmpty()) {
				break;
			}
			inboxEventRepository.deleteAllById(ids);
			total += ids.size();
			if (ids.size() < batch) {
				break;
			}
		}
		return total;
	}
}
