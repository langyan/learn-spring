package com.lin.spring.events.inbox;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.inbox.entity.InboxEvent;
import com.lin.spring.events.inbox.entity.InboxStatus;
import com.lin.spring.events.inbox.repository.InboxEventRepository;
import com.lin.spring.events.retry.ExponentialBackoff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Inbox 结果回写：独立新事务落盘，崩溃时记录停留在 PROCESSING 由陈旧锁回收兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InboxMarkService {

	private final InboxEventRepository inboxEventRepository;

	private final EventsProperties properties;

	/**
	 * 标记处理成功。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markProcessed(InboxEvent event) {
		InboxEvent current = inboxEventRepository.findById(event.getId()).orElse(null);
		if (current == null || current.getStatus() != InboxStatus.PROCESSING) {
			return;
		}
		current.setStatus(InboxStatus.PROCESSED);
		current.setProcessedAt(Instant.now());
		current.setLastError(null);
		current.setLockOwner(null);
		current.setLockedAt(null);
		inboxEventRepository.save(current);
	}

	/**
	 * 标记处理失败：未耗尽 → RECEIVED + 指数退避；耗尽 → FAILED + availableAt 置空。
	 *
	 * @return 是否已耗尽
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean markFailed(InboxEvent event, Throwable error) {
		InboxEvent current = inboxEventRepository.findById(event.getId()).orElse(null);
		if (current == null || current.getStatus() != InboxStatus.PROCESSING) {
			return false;
		}
		int maxAttempts = properties.getInbox().getMaxAttempts();
		current.setLastError(truncate(error.getMessage(), 1000));
		current.setLockOwner(null);
		current.setLockedAt(null);

		if (current.getAttempts() >= maxAttempts) {
			current.setStatus(InboxStatus.FAILED);
			current.setAvailableAt(null);
			log.error("Inbox event exhausted retries: eventId={} attempts={} → FAILED",
				current.getEventId(), current.getAttempts());
			return true;
		}

		long delayMs = ExponentialBackoff.computeDelayMs(
			current.getAttempts(),
			properties.getInbox().getBaseDelayMs(),
			properties.getInbox().getMaxDelayMs(),
			properties.getInbox().getJitterRatio());
		current.setStatus(InboxStatus.RECEIVED);
		current.setAvailableAt(Instant.now().plusMillis(delayMs));
		log.warn("Inbox handler failed, scheduled retry: eventId={} attempts={}/{} retryInMs={}",
			current.getEventId(), current.getAttempts(), maxAttempts, delayMs);
		return false;
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return null;
		}
		return s.length() <= max ? s : s.substring(0, max);
	}
}
