package com.lin.spring.orders.inventory.outbox;

import com.lin.spring.orders.common.event.EventJson;
import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.common.event.Topics;
import com.lin.spring.orders.inventory.domain.OutboxEvent;
import com.lin.spring.orders.inventory.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/** 与 order-service 的 OutboxPublisher 同构：SKIP LOCKED 批量取件 + 至少一次投递 + 消费者幂等。 */
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
                        event.getId(), event.getEventType(), event.getAggregateId(),
                        event.getCustomerId(), event.getCreatedAt(), event.getSequence(),
                        EventJson.decodePayload(event.getPayload()));
                kafkaTemplate.send(Topics.topicOf(event.getEventType()),
                                event.getAggregateId().toString(), EventJson.encode(envelope))
                        .get(sendTimeoutSeconds, TimeUnit.SECONDS);
                outboxRepository.markPublished(event.getId(), Instant.now());
                published++;
            } catch (Exception e) {
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
