package com.lin.spring.elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.elasticsearch.entity.Outbox;
import com.lin.spring.elasticsearch.entity.OutboxStatus;
import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.event.ProductChangedEvent;
import com.lin.spring.elasticsearch.event.ProductData;
import com.lin.spring.elasticsearch.event.ProductEvent;
import com.lin.spring.elasticsearch.event.ProductOperation;
import com.lin.spring.elasticsearch.repository.OutboxRepository;
import com.lin.spring.elasticsearch.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Event listener for product changes
 * Publishes events to Kafka with Outbox fallback
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProductEventListener {

    private final KafkaTemplate<String, ProductEvent> kafkaTemplate;
    private final ProductJpaRepository productJpaRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    private static final String KAFKA_TOPIC = "product-events";
    private static final long KAFKA_TIMEOUT_SECONDS = 3;

    /**
     * Handle product changed event after transaction commit
     */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleProductChangedEvent(ProductChangedEvent event) {
        log.info("Handling ProductChangedEvent: productId={}, operation={}",
                event.productId(), event.operation());

        try {
            // Fetch product data for CREATE and UPDATE operations
            ProductData productData = null;
            if (event.operation() != ProductOperation.DELETE) {
                Product product = productJpaRepository.findById(event.productId())
                        .orElseThrow(() -> new RuntimeException(
                                "Product not found: " + event.productId()
                        ));

                productData = convertToProductData(product);
            }

            // Create ProductEvent
            ProductEvent productEvent;
            if (event.operation() == ProductOperation.CREATE) {
                productEvent = ProductEvent.create(productData);
            } else if (event.operation() == ProductOperation.UPDATE) {
                productEvent = ProductEvent.update(productData);
            } else {
                productEvent = ProductEvent.delete(event.productId());
            }

            // Send to Kafka with timeout
            sendToKafkaWithTimeout(productEvent);

            log.info("Successfully published event to Kafka: eventId={}", productEvent.getEventId());

        } catch (Exception e) {
            log.error("Failed to publish event to Kafka, saving to Outbox: {}", e.getMessage(), e);

            // Fallback to Outbox pattern
            saveToOutbox(event);
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
     * Save event to Outbox for later processing
     */
    private void saveToOutbox(ProductChangedEvent event) {
        try {
            ProductData productData = null;
            if (event.operation() != ProductOperation.DELETE) {
                Product product = productJpaRepository.findById(event.productId())
                        .orElse(null);

                if (product != null) {
                    productData = convertToProductData(product);
                }
            }

            // Create ProductEvent for serialization
            ProductEvent productEvent;
            if (event.operation() == ProductOperation.CREATE && productData != null) {
                productEvent = ProductEvent.create(productData);
            } else if (event.operation() == ProductOperation.UPDATE && productData != null) {
                productEvent = ProductEvent.update(productData);
            } else {
                productEvent = ProductEvent.delete(event.productId());
            }

            // Convert to JSON
            String payload = objectMapper.writeValueAsString(productEvent);

            Outbox outbox = Outbox.builder()
                    .aggregateType("Product")
                    .aggregateId(event.productId().toString())
                    .eventType(event.operation().name())
                    .payload(payload)
                    .status(OutboxStatus.PENDING)
                    .createdAt(LocalDateTime.now())
                    .retryCount(0)
                    .build();

            outboxRepository.save(outbox);

            log.info("Event saved to Outbox: eventId={}, aggregateId={}, operation={}",
                    outbox.getEventId(), event.productId(), event.operation());

        } catch (Exception e) {
            log.error("Failed to save event to Outbox: {}", e.getMessage(), e);
            // This is a critical failure - log but don't throw to avoid breaking the flow
        }
    }

    /**
     * Convert Product entity to ProductData DTO
     */
    private ProductData convertToProductData(Product product) {
        return ProductData.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(java.math.BigDecimal.valueOf(product.getPrice()))
                .category(product.getCategory())
                .tags(product.getTagsAsList())
                .build();
    }
}
