package com.lin.spring.orders.payment.outbox;

import com.lin.spring.orders.common.event.EventJson;
import com.lin.spring.orders.payment.domain.OutboxEvent;
import com.lin.spring.orders.payment.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private final OutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void append(UUID aggregateId, Long customerId, String eventType, Map<String, Object> payload) {
        outboxRepository.save(OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(aggregateId)
                .customerId(customerId)
                .eventType(eventType)
                .sequence(outboxRepository.nextSequence(aggregateId))
                .payload(EventJson.encodePayload(payload))
                .build());
    }
}
