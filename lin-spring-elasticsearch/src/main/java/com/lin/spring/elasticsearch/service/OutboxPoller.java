package com.lin.spring.elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.elasticsearch.entity.Outbox;
import com.lin.spring.elasticsearch.entity.OutboxStatus;
import com.lin.spring.elasticsearch.event.ProductEvent;
import com.lin.spring.elasticsearch.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled task to poll and process Outbox messages
 * Implements the Outbox pattern for reliable event delivery
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPoller {

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    private static final String KAFKA_TOPIC = "product-events";
    private static final int MAX_MESSAGES_PER_POLL = 100;
    private static final int MAX_RETRY_COUNT = 5;
    private static final long KAFKA_TIMEOUT_SECONDS = 3;

    /**
     * Poll pending outbox messages every 30 seconds
     * Uses fixedDelay to ensure previous execution completes before next run
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 10000)
    public void pollPendingMessages() {
        log.debug("Polling for pending outbox messages...");

        try {
            // Query pending messages ordered by creation time
            List<Outbox> pendingMessages = outboxRepository
                    .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
                    .stream()
                    .limit(MAX_MESSAGES_PER_POLL)
                    .toList();

            if (pendingMessages.isEmpty()) {
                log.debug("No pending outbox messages found");
                return;
            }

            log.info("Found {} pending outbox messages", pendingMessages.size());

            // Process each message
            for (Outbox outbox : pendingMessages) {
                processOutboxMessage(outbox);
            }

        } catch (Exception e) {
            log.error("Error during outbox polling: {}", e.getMessage(), e);
        }
    }

    /**
     * Process a single outbox message
     */
    private void processOutboxMessage(Outbox outbox) {
        log.debug("Processing outbox message: eventId={}, aggregateId={}, eventType={}",
                outbox.getEventId(),
                outbox.getAggregateId(),
                outbox.getEventType());

        try {
            // Deserialize payload to ProductEvent
            ProductEvent productEvent = objectMapper.readValue(
                    outbox.getPayload(),
                    ProductEvent.class
            );

            // Send to Kafka with timeout
            sendToKafkaWithTimeout(productEvent);

            // Update status to PUBLISHED
            outbox.setStatus(OutboxStatus.PUBLISHED);
            outbox.setProcessedAt(LocalDateTime.now());
            outbox.setErrorMessage(null);

            outboxRepository.save(outbox);

            log.info("Outbox message published successfully: eventId={}", outbox.getEventId());

        } catch (Exception e) {
            log.error("Failed to process outbox message: eventId={}, error={}",
                    outbox.getEventId(), e.getMessage());

            handleFailedOutboxMessage(outbox, e);
        }
    }

    /**
     * Send event to Kafka with timeout
     */
    private void sendToKafkaWithTimeout(ProductEvent productEvent) throws Exception {
        CompletableFuture<SendResult<String, ProductEvent>> future =
                kafkaTemplate.send(KAFKA_TOPIC, productEvent.getEventId(), productEvent);

        try {
            future.get(KAFKA_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Kafka send failed or timed out after {}s: {}",
                    KAFKA_TIMEOUT_SECONDS, e.getMessage());
            throw e;
        }
    }

    /**
     * Handle failed outbox message
     * Increment retry count and mark as FAILED after max retries
     */
    private void handleFailedOutboxMessage(Outbox outbox, Exception e) {
        try {
            int currentRetryCount = outbox.getRetryCount() != null ? outbox.getRetryCount() : 0;
            int newRetryCount = currentRetryCount + 1;

            outbox.setRetryCount(newRetryCount);
            outbox.setErrorMessage(e.getMessage());

            if (newRetryCount >= MAX_RETRY_COUNT) {
                // Mark as FAILED after max retries
                outbox.setStatus(OutboxStatus.FAILED);
                log.error("Outbox message marked as FAILED after {} retries: eventId={}",
                        MAX_RETRY_COUNT, outbox.getEventId());
            } else {
                log.warn("Outbox message retry count incremented to {}: eventId={}",
                        newRetryCount, outbox.getEventId());
            }

            outboxRepository.save(outbox);

        } catch (Exception saveException) {
            log.error("Failed to update outbox message status: eventId={}, error={}",
                    outbox.getEventId(), saveException.getMessage());
        }
    }

    /**
     * Optional: Scheduled task to clean up old published messages
     * Runs once per day at midnight
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupOldPublishedMessages() {
        log.info("Cleaning up old published outbox messages...");

        try {
            // Delete messages published more than 7 days ago
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(7);

            List<Outbox> oldMessages = outboxRepository
                    .findByStatusOrderByCreatedAtAsc(OutboxStatus.PUBLISHED)
                    .stream()
                    .filter(msg -> msg.getProcessedAt() != null &&
                                msg.getProcessedAt().isBefore(cutoffDate))
                    .toList();

            if (!oldMessages.isEmpty()) {
                outboxRepository.deleteAll(oldMessages);
                log.info("Deleted {} old published outbox messages", oldMessages.size());
            } else {
                log.info("No old published messages to delete");
            }

        } catch (Exception e) {
            log.error("Error during outbox cleanup: {}", e.getMessage(), e);
        }
    }
}
