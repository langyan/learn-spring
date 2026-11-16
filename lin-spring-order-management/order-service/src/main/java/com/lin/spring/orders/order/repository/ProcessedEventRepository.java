package com.lin.spring.orders.order.repository;

import com.lin.spring.orders.order.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    /**
     * 幂等登记：插入冲突（事件已处理过）返回 0 行，调用方直接跳过。
     * 与业务更新同事务提交——要么都生效，要么都回滚。
     */
    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, event_type)
            VALUES (:eventId, :eventType)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int tryRegister(@Param("eventId") UUID eventId, @Param("eventType") String eventType);
}
