package com.lin.spring.elasticsearch.event;

/**
 * Spring application event for product changes
 */
public record ProductChangedEvent(Long productId, ProductOperation operation) {
}
