package com.lin.spring.moviebooking.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.moviebooking.booking.domain.OutboxEvent;
import com.lin.spring.moviebooking.booking.repository.OutboxEventRepository;
import com.lin.spring.moviebooking.common.enums.EventTopic;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Appends an event to the outbox INSIDE the caller's transaction.
 * The relay publishes it to Kafka only after commit — listeners never
 * observe a booking that is not durable yet.
 */
@Service
@RequiredArgsConstructor
public class OutboxAppender {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;

    public void append(EventTopic topic, String aggregateId, Map<String, Object> payload) {
        OutboxEvent event = new OutboxEvent();
        event.setEventId(UUID.randomUUID().toString());
        event.setTopic(topic.topicName());
        event.setAggregateId(aggregateId);
        event.setPayload(serialize(payload));
        event.setStatus("PENDING");
        event.setAttempts(0);
        event.setCreatedAt(Instant.now());
        repository.save(event);
    }

    private String serialize(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            throw new IllegalStateException("Outbox payload serialization failed", ex);
        }
    }
}
