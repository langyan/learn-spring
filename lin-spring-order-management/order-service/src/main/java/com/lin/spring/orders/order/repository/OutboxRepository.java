package com.lin.spring.orders.order.repository;

import com.lin.spring.orders.order.domain.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * 批量锁定未发布事件：FOR UPDATE SKIP LOCKED 保证多实例安全（互不阻塞、不重复取件）。
     * 必须运行在事务内（行锁随事务提交释放）。
     */
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

    /** 聚合内下一个事件序号（同事务内计算，保证单调递增）。 */
    @Query("SELECT COALESCE(MAX(e.sequence), 0) + 1 FROM OutboxEvent e WHERE e.aggregateId = :aggregateId")
    long nextSequence(@Param("aggregateId") UUID aggregateId);

    long countByPublishedAtIsNull();
}
