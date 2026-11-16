package com.lin.spring.events.outbox;

import com.lin.spring.events.config.EventsProperties;
import com.lin.spring.events.outbox.entity.OutboxEvent;
import com.lin.spring.events.outbox.entity.OutboxStatus;
import com.lin.spring.events.outbox.repository.OutboxEventRepository;
import com.lin.spring.events.retry.ExponentialBackoff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Outbox 结果回写：每次投递结束都在<b>独立新事务</b>中落盘结果，
 * 崩溃时记录停留在 PROCESSING，由陈旧锁回收兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxMarkService {

	private final OutboxEventRepository outboxEventRepository;

	private final EventsProperties properties;

	/**
	 * 标记投递成功。
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markPublished(OutboxEvent event) {
		OutboxEvent current = outboxEventRepository.findById(event.getId()).orElse(null);
		if (current == null || current.getStatus() != OutboxStatus.PROCESSING) {
			return;
		}
		current.setStatus(OutboxStatus.PUBLISHED);
		current.setPublishedAt(Instant.now());
		current.setLastError(null);
		current.setLockOwner(null);
		current.setLockedAt(null);
		outboxEventRepository.save(current);
	}

	/**
	 * 标记投递失败：未耗尽 → PENDING + 指数退避；耗尽 → FAILED + availableAt 置空停止自动重试。
	 *
	 * @param error 失败原因（截断后写 last_error）
	 * @return 是否已耗尽（FAILED）
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean markFailed(OutboxEvent event, Throwable error) {
		OutboxEvent current = outboxEventRepository.findById(event.getId()).orElse(null);
		if (current == null || current.getStatus() != OutboxStatus.PROCESSING) {
			return false;
		}
		int maxAttempts = properties.getOutbox().getMaxAttempts();
		current.setLastError(truncate(error.getMessage(), 1000));
		current.setLockOwner(null);
		current.setLockedAt(null);

		if (current.getAttempts() >= maxAttempts) {
			current.setStatus(OutboxStatus.FAILED);
			current.setAvailableAt(null);
			log.error("Outbox event exhausted retries: id={} eventId={} attempts={} → FAILED",
				current.getId(), current.getEventId(), current.getAttempts());
			return true;
		}

		long delayMs = ExponentialBackoff.computeDelayMs(
			current.getAttempts(),
			properties.getOutbox().getBaseDelayMs(),
			properties.getOutbox().getMaxDelayMs(),
			properties.getOutbox().getJitterRatio());
		current.setStatus(OutboxStatus.PENDING);
		current.setAvailableAt(Instant.now().plusMillis(delayMs));
		log.warn("Outbox publish failed, scheduled retry: id={} eventId={} attempts={}/{} retryInMs={}",
			current.getId(), current.getEventId(), current.getAttempts(), maxAttempts, delayMs);
		return false;
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return null;
		}
		return s.length() <= max ? s : s.substring(0, max);
	}
}
