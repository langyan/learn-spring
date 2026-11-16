package com.lin.spring.moviebooking.booking.service;

import com.lin.spring.moviebooking.booking.domain.ProcessedEvent;
import com.lin.spring.moviebooking.booking.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;

/**
 * Notification consumer with inbox idempotency. The insert into
 * processed_events and the side effect share one transaction: if the side
 * effect fails, the marker rolls back and redelivery re-runs everything.
 * Redelivery after success hits the unique key and becomes a no-op.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationConsumer {

    private final ProcessedEventRepository processedEventRepository;
    private final TransactionTemplate transactionTemplate;

    @KafkaListener(topics = {
            "movie-booking.booking-confirmed",
            "movie-booking.booking-refunded",
            "movie-booking.booking-expired"
    })
    public void onMessage(String eventId, String payload, Acknowledgment ack) {
        try {
            boolean firstDelivery = transactionTemplate.execute(status -> {
                if (processedEventRepository.existsByEventId(eventId)) {
                    return false;                        // inbox hit: duplicate, skip
                }
                ProcessedEvent marker = new ProcessedEvent();
                marker.setEventId(eventId);
                marker.setEventType(topicOf(eventId, payload));
                marker.setProcessedAt(Instant.now());
                processedEventRepository.save(marker);
                // Side effect in the SAME transaction as the marker.
                log.info("[NOTIFICATION] event {} payload={}", eventId, payload);
                return true;
            });
            if (Boolean.TRUE.equals(firstDelivery)) {
                log.info("Notification delivered for event {}", eventId);
            }
            ack.acknowledge();
        } catch (Exception ex) {
            // No ack -> Kafka redelivers; the inbox marker guarantees at-most-once effects.
            log.error("Notification handling failed for event {}: {}", eventId, ex.getMessage());
        }
    }

    private String topicOf(String eventId, String payload) {
        // Payload carries the topic-agnostic business event; type is derived
        // from content for the demo (kept simple on purpose).
        return payload.contains("SEATS_LOST") ? "BOOKING_REFUNDED"
                : payload.contains("seatsReleased") ? "BOOKING_EXPIRED"
                : "BOOKING_CONFIRMED";
    }
}
