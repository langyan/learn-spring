package com.lin.spring.orders.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 跨服务集成事件的统一信封（envelope）。
 *
 * <p>每个服务通过事务性 Outbox 发布事件；消费者以 {@code eventId} 幂等去重，
 * 以 {@code aggregateId}（orderId）作为 Kafka 分区键保证单订单事件顺序，
 * 以 {@code sequence}（聚合版本号）识别过期/乱序事件。</p>
 */
public record IntegrationEvent(
        UUID eventId,
        String type,
        UUID aggregateId,
        Long customerId,
        Instant occurredAt,
        long sequence,
        Map<String, Object> payload) {

    @SuppressWarnings("unchecked")
    public <T> T payloadValue(String key) {
        return (T) payload.get(key);
    }
}
