package com.lin.spring.moviebooking.booking.service;

import com.lin.spring.moviebooking.booking.config.BookingProperties;
import com.lin.spring.moviebooking.booking.domain.OutboxEvent;
import com.lin.spring.moviebooking.booking.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Transactional-outbox relay: publishes committed events to Kafka.
 *
 * <p>Multi-instance safety comes from the conditional claim (status=PENDING ->
 * PROCESSING with lock owner) — concurrent relays can never hand the same row
 * to Kafka twice with different outcomes. Kafka itself is idempotent-enabled;
 * consumers deduplicate via the inbox anyway.
 *
 * <p>Retry policy: exponential backoff (base..max). After maxAttempts the row
 * lands in FAILED — the operational dead-letter that a human replays.</p>
 */
@Slf4j
@Component
public class OutboxRelayScheduler {

    private final OutboxEventRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final TransactionTemplate transactionTemplate;
    private final BookingProperties properties;
    private final String instanceId = "relay-" + UUID.randomUUID().toString().substring(0, 8);

    public OutboxRelayScheduler(OutboxEventRepository outboxRepository,
                                KafkaTemplate<String, String> kafkaTemplate,
                                TransactionTemplate transactionTemplate,
                                BookingProperties properties) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${movie-booking.outbox.poll-interval-ms:2000}",
            initialDelayString = "${movie-booking.outbox.poll-interval-ms:2000}")
    public void relay() {
        recoverStaleLocks();

        List<Long> claimed = transactionTemplate.execute(status -> {
            List<Long> candidates = outboxRepository.findCandidateIds(
                    Instant.now(), PageRequest.of(0, properties.getOutbox().getBatchSize()));
            if (candidates.isEmpty()) {
                return List.of();
            }
            int claimedCount = outboxRepository.claim(candidates, instanceId, Instant.now());
            if (claimedCount == 0) {
                return List.of();
            }
            return outboxRepository.findByLockOwnerAndStatus(instanceId, "PROCESSING").stream()
                    .map(OutboxEvent::getId)
                    .toList();
        });
        if (claimed == null || claimed.isEmpty()) {
            return;
        }

        for (Long eventId : claimed) {
            publish(eventId);
        }
    }

    private void recoverStaleLocks() {
        Instant threshold = Instant.now().minusSeconds(properties.getOutbox().getStaleLockSeconds());
        int recovered = outboxRepository.recoverStaleProcessing(threshold);
        if (recovered > 0) {
            log.warn("Recovered {} stale PROCESSING outbox rows (crashed relay?)", recovered);
        }
    }

    private void publish(long eventId) {
        OutboxEvent event = outboxRepository.findById(eventId).orElse(null);
        if (event == null) {
            return;
        }
        try {
            // Synchronous send: only a broker-acked write counts as published.
            kafkaTemplate.send(event.getTopic(), event.getEventId(), event.getPayload())
                    .get(5, TimeUnit.SECONDS);
            transactionTemplate.executeWithoutResult(status ->
                    outboxRepository.findById(eventId).ifPresent(e -> {
                        e.setStatus("PUBLISHED");
                        e.setPublishedAt(Instant.now());
                        e.setLockOwner(null);
                        e.setLockedAt(null);
                    }));
            log.debug("Outbox {} published to {}", eventId, event.getTopic());
        } catch (Exception ex) {
            String error = truncate(ex.getMessage());
            long backoffMs = Math.min(
                    properties.getOutbox().getBaseDelayMs() << event.getAttempts(),
                    properties.getOutbox().getMaxDelayMs());
            transactionTemplate.executeWithoutResult(status ->
                    outboxRepository.findById(eventId).ifPresent(e -> {
                        boolean dead = e.getAttempts() >= properties.getOutbox().getMaxAttempts();
                        e.setStatus(dead ? "FAILED" : "PENDING");
                        e.setLastError(error);
                        e.setAvailableAt(Instant.now().plusMillis(backoffMs));
                        e.setLockOwner(null);
                        e.setLockedAt(null);
                        if (dead) {
                            log.error("OUTBOX DEAD LETTER: event {} to topic {} after {} attempts: {}",
                                    e.getEventId(), e.getTopic(), e.getAttempts(), error);
                        }
                    }));
        }
    }

    private String truncate(String message) {
        if (message == null) {
            return "unknown";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
