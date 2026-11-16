package com.lin.spring.orders.order.outbox;

import com.lin.spring.orders.common.event.EventJson;
import com.lin.spring.orders.order.domain.Order;
import com.lin.spring.orders.order.domain.OutboxEvent;
import com.lin.spring.orders.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Outbox 写入器：与业务变更同一事务（REQUIRED 传播，调用方事务回滚则事件一并丢弃），
 * 从根上解决"订单落库成功但事件发布失败"的双写问题。
 */
@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private final OutboxRepository outboxRepository;

    @Transactional(propagation = Propagation.REQUIRED)
    public void append(Order order, String eventType, Map<String, Object> payload) {
        outboxRepository.save(OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateId(order.getId())
                .customerId(order.getCustomerId())
                .eventType(eventType)
                .sequence(outboxRepository.nextSequence(order.getId()))
                .payload(EventJson.encodePayload(payload))
                .build());
    }
}
