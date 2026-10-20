package com.lin.spring.webhook.service;

import com.lin.spring.webhook.domain.DeliveryStatus;
import com.lin.spring.webhook.domain.OutcomeKind;
import com.lin.spring.webhook.dto.DeliveryResult;
import com.lin.spring.webhook.entity.DeliveryAttemptEntity;
import com.lin.spring.webhook.entity.WebhookEventEntity;
import com.lin.spring.webhook.repository.DeliveryAttemptRepository;
import com.lin.spring.webhook.repository.WebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 投递流水与事件状态落库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryLogService {

	private final WebhookEventRepository eventRepository;
	private final DeliveryAttemptRepository attemptRepository;

	/**
	 * 记录一次投递尝试。
	 */
	@Transactional
	public void recordAttempt(String eventId, String endpointId, int attempt, DeliveryResult result,
							  OutcomeKind outcome) {
		DeliveryAttemptEntity entity = new DeliveryAttemptEntity(eventId, endpointId, attempt);
		if (result != null) {
			entity.setStatusCode(result.statusCode());
			entity.setLatencyMs(result.latencyMs());
			entity.setSuccess(result.success());
			entity.setError(result.error());
		}
		entity.setOutcome(outcome.name());
		attemptRepository.save(entity);
	}

	/**
	 * 更新事件状态。
	 */
	@Transactional
	public void markStatus(String eventId, DeliveryStatus status, String lastError, Instant nextAttemptAt) {
		eventRepository.findByEventId(eventId).ifPresent(entity -> {
			entity.setStatus(status);
			entity.setLastError(lastError);
			entity.setNextAttemptAt(nextAttemptAt);
			entity.setUpdatedAt(Instant.now());
			eventRepository.save(entity);
		});
	}

	@Transactional
	public void markAttempt(String eventId, int attempt, DeliveryStatus status, String lastError,
							Instant nextAttemptAt) {
		eventRepository.findByEventId(eventId).ifPresent(entity -> {
			entity.setAttempt(attempt);
			entity.setStatus(status);
			entity.setLastError(lastError);
			entity.setNextAttemptAt(nextAttemptAt);
			entity.setUpdatedAt(Instant.now());
			eventRepository.save(entity);
		});
	}

	@Transactional(readOnly = true)
	public List<DeliveryAttemptEntity> attemptsFor(String eventId) {
		return attemptRepository.findByEventIdOrderByAttemptAsc(eventId);
	}

	@Transactional(readOnly = true)
	public Optional<WebhookEventEntity> event(String eventId) {
		return eventRepository.findByEventId(eventId);
	}

	@Transactional(readOnly = true)
	public List<WebhookEventEntity> deadLetters() {
		return eventRepository.findByStatusOrderByCreatedAtDesc(DeliveryStatus.FAILED);
	}

	@Transactional(readOnly = true)
	public List<WebhookEventEntity> cancelledEvents() {
		return eventRepository.findByStatusOrderByCreatedAtDesc(DeliveryStatus.CANCELLED);
	}
}
