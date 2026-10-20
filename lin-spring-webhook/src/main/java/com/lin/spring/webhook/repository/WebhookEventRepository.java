package com.lin.spring.webhook.repository;

import com.lin.spring.webhook.domain.DeliveryStatus;
import com.lin.spring.webhook.entity.WebhookEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 事件仓储。
 */
public interface WebhookEventRepository extends JpaRepository<WebhookEventEntity, Long> {

	Optional<WebhookEventEntity> findByEventId(String eventId);

	boolean existsByEventId(String eventId);

	List<WebhookEventEntity> findByStatusOrderByCreatedAtDesc(DeliveryStatus status);
}
