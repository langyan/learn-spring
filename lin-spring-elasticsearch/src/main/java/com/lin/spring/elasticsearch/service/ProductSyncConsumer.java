package com.lin.spring.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import com.lin.spring.elasticsearch.event.ProductEvent;
import com.lin.spring.elasticsearch.event.ProductOperation;
import com.lin.spring.elasticsearch.repository.ProductElasticsearchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for syncing product events to Elasticsearch
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSyncConsumer {

    private final ProductElasticsearchRepository productElasticsearchRepository;
    private final ElasticsearchClient elasticsearchClient;

    private static final String PRODUCT_INDEX = "products";

    /**
     * Consume product events from Kafka and sync to Elasticsearch
     * Uses manual acknowledgment for reliability
     */
    @KafkaListener(
            topics = "product-events",
            groupId = "product-sync-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeProductEvent(
            @Payload ProductEvent productEvent,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment
    ) {
        log.info("Consuming event: eventId={}, operation={}, productId={}, partition={}, offset={}",
                productEvent.getEventId(),
                productEvent.getOperation(),
                productEvent.getProductData() != null ? productEvent.getProductData().getId() : "N/A",
                partition,
                offset);

        try {
            switch (productEvent.getOperation()) {
                case CREATE:
                    handleCreatedEvent(productEvent);
                    break;

                case UPDATE:
                    handleUpdatedEvent(productEvent);
                    break;

                case DELETE:
                    handleDeletedEvent(productEvent);
                    break;

                default:
                    log.warn("Unknown operation: {}", productEvent.getOperation());
            }

            // Manual acknowledgment after successful processing
            if (acknowledgment != null) {
                acknowledgment.acknowledge();
                log.info("Event acknowledged: eventId={}", productEvent.getEventId());
            }

        } catch (Exception e) {
            log.error("Failed to process event: eventId={}, operation={}, error={}",
                    productEvent.getEventId(),
                    productEvent.getOperation(),
                    e.getMessage(), e);

            // Throw exception to trigger retry
            throw new RuntimeException("Failed to sync product to Elasticsearch: " + e.getMessage(), e);
        }
    }

    /**
     * Handle CREATED event - save to Elasticsearch
     */
    private void handleCreatedEvent(ProductEvent productEvent) {
        log.info("Handling CREATED event for product: {}",
                productEvent.getProductData().getId());

        ProductDocument document = convertToDocument(productEvent);
        productElasticsearchRepository.save(document);

        log.info("Product indexed: id={}", document.getId());
    }

    /**
     * Handle UPDATED event - update in Elasticsearch
     */
    private void handleUpdatedEvent(ProductEvent productEvent) {
        log.info("Handling UPDATED event for product: {}",
                productEvent.getProductData().getId());

        ProductDocument document = convertToDocument(productEvent);
        productElasticsearchRepository.save(document);

        log.info("Product updated in index: id={}", document.getId());
    }

    /**
     * Handle DELETED event - delete from Elasticsearch
     */
    private void handleDeletedEvent(ProductEvent productEvent) throws Exception {
        Long productId = productEvent.getProductData().getId();
        log.info("Handling DELETED event for product: {}", productId);

        try {
            // Delete using ElasticsearchClient for more control
            DeleteRequest deleteRequest = DeleteRequest.of(d -> d
                    .index(PRODUCT_INDEX)
                    .id(String.valueOf(productId))
            );

            elasticsearchClient.delete(deleteRequest);

            log.info("Product deleted from index: id={}", productId);

        } catch (Exception e) {
            log.warn("Failed to delete product from Elasticsearch: {}, error={}",
                    productId, e.getMessage());

            // If document doesn't exist, that's okay - it might have been already deleted
            if (!e.getMessage().contains("index_not_found_exception") &&
                !e.getMessage().contains("document_missing_exception")) {
                throw new RuntimeException("Failed to delete product from Elasticsearch", e);
            }

            log.info("Product already deleted or doesn't exist: {}", productId);
        }
    }

    /**
     * Convert ProductEvent to ProductDocument
     */
    private ProductDocument convertToDocument(ProductEvent productEvent) {
        var data = productEvent.getProductData();

        return ProductDocument.builder()
                .id(String.valueOf(data.getId()))
                .name(data.getName())
                .description(data.getDescription())
                .price(data.getPrice() != null ? data.getPrice().doubleValue() : null)
                .category(data.getCategory())
                .tags(data.getTags())
                .createdAt(productEvent.getTimestamp())
                .build();
    }
}
