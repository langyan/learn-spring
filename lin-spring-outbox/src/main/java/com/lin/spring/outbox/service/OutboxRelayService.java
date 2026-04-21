package com.lin.spring.outbox.service;

import com.lin.spring.outbox.entity.OutboxMessage;
import com.lin.spring.outbox.entity.OutboxStatus;
import com.lin.spring.outbox.outbox.OutboxMessagePublisher;
import com.lin.spring.outbox.repository.OutboxMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 轮询 Outbox 并调用 {@link OutboxMessagePublisher} 完成投递，更新状态。
 */
@Slf4j
@Service
public class OutboxRelayService {

	private final OutboxMessageRepository outboxMessageRepository;

	private final OutboxMessagePublisher outboxMessagePublisher;

	private final int batchSize;

	private final int maxRetries;

	public OutboxRelayService(
		OutboxMessageRepository outboxMessageRepository,
		OutboxMessagePublisher outboxMessagePublisher,
		@Value("${app.outbox.relay.batch-size:20}") int batchSize,
		@Value("${app.outbox.relay.max-retries:3}") int maxRetries
	) {
		this.outboxMessageRepository = outboxMessageRepository;
		this.outboxMessagePublisher = outboxMessagePublisher;
		this.batchSize = batchSize;
		this.maxRetries = maxRetries;
	}

	/**
	 * 处理一批待投递记录（供调度器与测试调用）。
	 *
	 * @return 本批尝试处理的条数
	 */
	public int processPendingBatch() {
		List<OutboxMessage> pending = outboxMessageRepository
			.findByStatusOrderByIdAsc(OutboxStatus.PENDING)
			.stream()
			.limit(batchSize)
			.toList();

		int n = 0;
		for (OutboxMessage msg : pending) {
			processOne(msg.getId());
			n++;
		}
		return n;
	}

	/**
	 * 在独立事务中处理单条，避免一条失败影响整批已提交的状态更新。
	 */
	@Transactional
	public void processOne(Long outboxId) {
		OutboxMessage message = outboxMessageRepository.findById(outboxId).orElse(null);
		if (message == null || message.getStatus() != OutboxStatus.PENDING) {
			return;
		}
		try {
			outboxMessagePublisher.publish(message);
			message.setStatus(OutboxStatus.PUBLISHED);
			message.setProcessedAt(Instant.now());
			message.setErrorMessage(null);
			outboxMessageRepository.save(message);
		} catch (Exception e) {
			log.warn("Outbox publish failed: id={} eventId={} err={}",
				message.getId(), message.getEventId(), e.getMessage());
			message.setRetryCount(message.getRetryCount() + 1);
			message.setErrorMessage(truncate(e.getMessage(), 1000));
			if (message.getRetryCount() >= maxRetries) {
				message.setStatus(OutboxStatus.FAILED);
			}
			outboxMessageRepository.save(message);
		}
	}

	private static String truncate(String s, int max) {
		if (s == null) {
			return null;
		}
		return s.length() <= max ? s : s.substring(0, max);
	}
}
