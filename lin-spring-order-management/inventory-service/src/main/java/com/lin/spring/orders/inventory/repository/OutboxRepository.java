package com.lin.spring.orders.inventory.repository;

import com.lin.spring.orders.inventory.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    @Query(value = """
            SELECT * FROM outbox_events
             WHERE published_at IS NULL
             ORDER BY created_at
             LIMIT :batchSize
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockUnpublishedBatch(@Param("batchSize") int batchSize);

    @Modifying
    @Query("UPDATE OutboxEvent e SET e.publishedAt = :publishedAt WHERE e.id = :id")
    void markPublished(@Param("id") UUID id, @Param("publishedAt") Instant publishedAt);

    @Query("SELECT COALESCE(MAX(e.sequence), 0) + 1 FROM OutboxEvent e WHERE e.aggregateId = :aggregateId")
    long nextSequence(@Param("aggregateId") UUID aggregateId);

    long countByPublishedAtIsNull();
}
