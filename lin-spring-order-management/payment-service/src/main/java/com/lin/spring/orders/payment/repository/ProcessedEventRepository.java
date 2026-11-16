package com.lin.spring.orders.payment.repository;

import com.lin.spring.orders.payment.domain.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO processed_events (event_id, event_type)
            VALUES (:eventId, :eventType)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int tryRegister(@Param("eventId") UUID eventId, @Param("eventType") String eventType);
}
