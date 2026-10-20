package com.lin.spring.webhook.repository;

import com.lin.spring.webhook.entity.DeliveryAttemptEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 投递尝试流水仓储。
 */
public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttemptEntity, Long> {

	List<DeliveryAttemptEntity> findByEventIdOrderByAttemptAsc(String eventId);
}
