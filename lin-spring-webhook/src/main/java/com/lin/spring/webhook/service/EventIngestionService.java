package com.lin.spring.webhook.service;

import com.lin.spring.webhook.domain.DeliveryStatus;
import com.lin.spring.webhook.dto.IngestEventRequest;
import com.lin.spring.webhook.dto.WebhookEvent;
import com.lin.spring.webhook.entity.WebhookEventEntity;
import com.lin.spring.webhook.exception.SsrfBlockedException;
import com.lin.spring.webhook.publisher.EventBus;
import com.lin.spring.webhook.repository.WebhookEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * 事件接入：先落库再异步投递，兑现「内部一旦接受，就一定会尝试投递」的承诺。
 */
@Slf4j
@Service
public class EventIngestionService {

	private final WebhookEventRepository eventRepository;
	private final EventBus eventBus;

	public EventIngestionService(WebhookEventRepository eventRepository, EventBus eventBus) {
		this.eventRepository = eventRepository;
		this.eventBus = eventBus;
	}

	/**
	 * 接收事件。
	 *
	 * @param request 入参
	 * @return 落库后的事件（幂等：同 eventId 重复提交直接返回已有记录）
	 */
	@Transactional
	public WebhookEventEntity ingest(IngestEventRequest request) {
		String eventId = request.getEventId() == null || request.getEventId().isBlank()
			? "evt_" + UUID.randomUUID().toString().substring(0, 12)
			: request.getEventId();

		if (eventRepository.existsByEventId(eventId)) {
			log.info("事件重复提交，直接返回 eventId={}", eventId);
			return eventRepository.findByEventId(eventId).orElseThrow();
		}

		WebhookEventEntity entity = new WebhookEventEntity();
		entity.setEventId(eventId);
		entity.setMerchantId(request.getMerchantId());
		entity.setEventType(request.getEventType());
		entity.setPayload(request.getPayload() == null ? "{}" : request.getPayload());
		entity.setStatus(DeliveryStatus.PENDING);
		entity.setCreatedAt(Instant.now());
		entity.setUpdatedAt(Instant.now());
		WebhookEventEntity saved = eventRepository.save(entity);

		eventBus.publish(new WebhookEvent(
			saved.getEventId(), saved.getMerchantId(), saved.getEventType(), saved.getPayload(), saved.getCreatedAt()));
		return saved;
	}

	/**
	 * 重放一条失败事件（DLQ 人工干预入口）。
	 */
	@Transactional
	public WebhookEventEntity replay(String eventId) {
		WebhookEventEntity entity = eventRepository.findByEventId(eventId)
			.orElseThrow(() -> new IllegalArgumentException("事件不存在: " + eventId));
		if (entity.getStatus() != DeliveryStatus.FAILED && entity.getStatus() != DeliveryStatus.CANCELLED) {
			throw new IllegalStateException("仅失败或已取消的事件可重放，当前状态: " + entity.getStatus());
		}
		entity.setStatus(DeliveryStatus.PENDING);
		entity.setAttempt(0);
		entity.setLastError(null);
		entity.setNextAttemptAt(null);
		entity.setUpdatedAt(Instant.now());
		WebhookEventEntity saved = eventRepository.save(entity);

		eventBus.publish(new WebhookEvent(
			saved.getEventId(), saved.getMerchantId(), saved.getEventType(), saved.getPayload(), saved.getCreatedAt()));
		return saved;
	}
}
