package com.lin.spring.elasticsearch.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Product event for Kafka messaging
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductEvent {

    private String eventId;
    private ProductOperation operation;
    private ProductData productData;
    private LocalDateTime timestamp;
    private String correlationId;

    /**
     * Factory method for CREATE event
     */
    public static ProductEvent create(ProductData productData) {
        return ProductEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .operation(ProductOperation.CREATE)
                .productData(productData)
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Factory method for UPDATE event
     */
    public static ProductEvent update(ProductData productData) {
        return ProductEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .operation(ProductOperation.UPDATE)
                .productData(productData)
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .build();
    }

    /**
     * Factory method for DELETE event
     */
    public static ProductEvent delete(Long productId) {
        ProductData productData = ProductData.builder()
                .id(productId)
                .build();
        return ProductEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .operation(ProductOperation.DELETE)
                .productData(productData)
                .timestamp(LocalDateTime.now())
                .correlationId(UUID.randomUUID().toString())
                .build();
    }
}
