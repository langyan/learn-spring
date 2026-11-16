package com.lin.spring.orders.notification.repository;

import com.lin.spring.orders.notification.domain.SentNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SentNotificationRepository extends JpaRepository<SentNotification, UUID> {

    @Modifying
    @Query(value = """
            INSERT INTO sent_notifications (event_id, event_type, channel, customer_id, order_id)
            VALUES (:eventId, :eventType, :channel, :customerId, :orderId)
            ON CONFLICT (event_id) DO NOTHING
            """, nativeQuery = true)
    int tryRegister(@Param("eventId") UUID eventId,
                    @Param("eventType") String eventType,
                    @Param("channel") String channel,
                    @Param("customerId") Long customerId,
                    @Param("orderId") UUID orderId);
}
