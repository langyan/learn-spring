package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.Outbox;
import com.lin.spring.elasticsearch.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Outbox entity
 */
@Repository
public interface OutboxRepository extends JpaRepository<Outbox, Long> {

    /**
     * Find pending messages ordered by creation time
     */
    List<Outbox> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    /**
     * Find messages by aggregate type and status
     */
    List<Outbox> findByAggregateTypeAndStatusOrderByCreatedAtAsc(
            String aggregateType,
            OutboxStatus status
    );

    /**
     * Find message by event ID
     */
    Optional<Outbox> findByEventId(String eventId);

    /**
     * Count messages by status
     */
    long countByStatus(OutboxStatus status);

    /**
     * Find published messages processed before given time
     */
    List<Outbox> findByStatusAndProcessedAtBeforeOrderByCreatedAtDesc(
            OutboxStatus status,
            java.time.LocalDateTime processedAt
    );
}
