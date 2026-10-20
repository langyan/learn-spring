package com.lin.spring.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.notification.domain.NotificationStatus;
import com.lin.spring.notification.dto.NotificationEvent;
import com.lin.spring.notification.dto.NotificationRequest;
import com.lin.spring.notification.dto.NotificationResponse;
import com.lin.spring.notification.entity.NotificationEntity;
import com.lin.spring.notification.publisher.NotificationEventPublisher;
import com.lin.spring.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

import static org.springframework.http.HttpStatus.CONFLICT;

/**
 * 通知 API 核心服务：持久化意图 → 发布 Kafka 事件 → 立即返回。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;
	private final PreferenceService preferenceService;
	private final TopicResolver topicResolver;
	private final NotificationEventPublisher eventPublisher;
	private final ObjectMapper objectMapper;

	/**
	 * 接收通知请求并异步投递。
	 *
	 * @param request 请求体
	 * @return 已接受的通知响应
	 */
	@Transactional
	public NotificationResponse send(NotificationRequest request) {
		String idempotencyKey = request.idempotencyKey() != null
			? request.idempotencyKey()
			: UUID.randomUUID().toString();

		notificationRepository.findByIdempotencyKey(idempotencyKey).ifPresent(existing -> {
			throw new ResponseStatusException(CONFLICT, "重复请求，通知已存在: " + existing.getId());
		});

		if (!preferenceService.isAllowed(request.userId(), request.type(), request.channel())) {
			String notificationId = UUID.randomUUID().toString();
			saveBlocked(notificationId, request, idempotencyKey);
			return new NotificationResponse(notificationId, NotificationStatus.BLOCKED, null,
				"用户已关闭该渠道通知");
		}

		boolean scheduled = request.scheduledAt() != null && request.scheduledAt().isAfter(Instant.now());
		String notificationId = UUID.randomUUID().toString();
		String correlationId = UUID.randomUUID().toString();
		NotificationStatus status = scheduled ? NotificationStatus.SCHEDULED : NotificationStatus.PENDING;

		NotificationEntity entity = new NotificationEntity(
			notificationId,
			request.userId(),
			request.type(),
			request.channel(),
			request.templateId(),
			toJson(request.data()),
			idempotencyKey,
			status,
			correlationId,
			request.scheduledAt()
		);
		notificationRepository.save(entity);

		if (!scheduled) {
			publishEvent(entity, request.data(), 0);
		}

		return new NotificationResponse(
			notificationId,
			status,
			correlationId,
			scheduled ? "已排程，将在指定时间发送" : "已接受，异步投递中"
		);
	}

	/**
	 * 将已持久化的通知发布到 Kafka（供定时调度器调用）。
	 *
	 * @param entity 通知实体
	 */
	public void publishScheduled(NotificationEntity entity) {
		publishEvent(entity, fromJson(entity.getPayloadJson()), 0);
		entity.setStatus(NotificationStatus.PENDING);
		entity.setUpdatedAt(Instant.now());
		notificationRepository.save(entity);
	}

	/**
	 * 按 ID 查询通知。
	 *
	 * @param id 通知 ID
	 * @return 通知实体
	 */
	public NotificationEntity getById(String id) {
		return notificationRepository.findById(id)
			.orElseThrow(() -> new ResponseStatusException(
				org.springframework.http.HttpStatus.NOT_FOUND, "通知不存在: " + id));
	}

	private void publishEvent(NotificationEntity entity, java.util.Map<String, Object> data, int retryCount) {
		String topic = topicResolver.resolve(entity.getType());
		NotificationEvent event = new NotificationEvent(
			entity.getId(),
			entity.getUserId(),
			entity.getType(),
			entity.getChannel(),
			entity.getTemplateId(),
			data,
			entity.getIdempotencyKey(),
			retryCount,
			entity.getCorrelationId()
		);
		eventPublisher.publish(topic, event);
	}

	private void saveBlocked(String notificationId, NotificationRequest request, String idempotencyKey) {
		NotificationEntity blocked = new NotificationEntity(
			notificationId,
			request.userId(),
			request.type(),
			request.channel(),
			request.templateId(),
			toJson(request.data()),
			idempotencyKey,
			NotificationStatus.BLOCKED,
			null,
			request.scheduledAt()
		);
		notificationRepository.save(blocked);
	}

	private String toJson(java.util.Map<String, Object> data) {
		if (data == null || data.isEmpty()) {
			return "{}";
		}
		try {
			return objectMapper.writeValueAsString(data);
		} catch (JsonProcessingException e) {
			throw new IllegalArgumentException("data 序列化失败", e);
		}
	}

	@SuppressWarnings("unchecked")
	private java.util.Map<String, Object> fromJson(String json) {
		if (json == null || json.isBlank()) {
			return java.util.Map.of();
		}
		try {
			return objectMapper.readValue(json, java.util.Map.class);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("payload 反序列化失败", e);
		}
	}
}
