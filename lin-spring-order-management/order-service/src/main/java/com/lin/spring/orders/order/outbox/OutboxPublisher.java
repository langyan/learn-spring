package com.lin.spring.orders.order.outbox;

import com.lin.spring.orders.common.event.EventJson;
import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.common.event.Topics;
import com.lin.spring.orders.order.domain.OutboxEvent;
import com.lin.spring.orders.order.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Outbox 发布器：轮询未发布事件 → 同步发送 Kafka（等待 broker 确认）→ 标记 published_at。
 *
 * <p>崩溃窗口：Kafka 确认后、标记前宕机 → 事件会被再次发布（至少一次投递）。
 * 这是设计预期：所有消费者以 eventId 幂等去重。FOR UPDATE SKIP LOCKED 保证多实例安全。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPublisher {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.outbox.batch-size}")
    private int batchSize;

    @Value("${app.outbox.send-timeout-seconds}")
    private int sendTimeoutSeconds;

    @Transactional
    @Scheduled(fixedDelayString = "${app.outbox.poll-fixed-delay-ms}")
    public void publishPending() {
        var batch = outboxRepository.lockUnpublishedBatch(batchSize);
        if (batch.isEmpty()) {
            return;
        }
        int published = 0;
        for (OutboxEvent event : batch) {
            try {
                IntegrationEvent envelope = new IntegrationEvent(
                        event.getId(),
                        event.getEventType(),
                        event.getAggregateId(),
                        event.getCustomerId(),
                        event.getCreatedAt(),
                        event.getSequence(),
                        EventJson.decodePayload(event.getPayload()));

                kafkaTemplate.send(Topics.topicOf(event.getEventType()),
                                event.getAggregateId().toString(),   // 分区键 = orderId，保证单订单顺序
                                EventJson.encode(envelope))
                        .get(sendTimeoutSeconds, TimeUnit.SECONDS);
                outboxRepository.markPublished(event.getId(), Instant.now());
                published++;
            } catch (Exception e) {
                // 单条失败不影响同批其他事件；未标记的下次轮询重试
                log.error("Failed to publish outbox event {} (type={}), will retry: {}",
                        event.getId(), event.getEventType(), e.getMessage());
            }
        }
        if (published > 0) {
            log.debug("Outbox published {}/{} events, backlog={}",
                    published, batch.size(), outboxRepository.countByPublishedAtIsNull());
        }
    }
}
