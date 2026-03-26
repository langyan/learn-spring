package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.dto.ProductCreateRequest;
import com.lin.spring.elasticsearch.dto.ProductUpdateRequest;
import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.event.ProductChangedEvent;
import com.lin.spring.elasticsearch.event.ProductOperation;
import com.lin.spring.elasticsearch.exception.ProductNotFoundException;
import com.lin.spring.elasticsearch.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for product write operations (CUD)
 * Publishes events for each operation
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductWriteService {

    private final ProductJpaRepository productJpaRepository;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Create a new product
     */
    @Transactional
    public Product create(ProductCreateRequest request) {
        log.info("Creating product: {}", request.getName());

        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice().doubleValue());
        product.setCategory(request.getCategory());
        product.setTagsFromList(request.getTags());

        Product savedProduct = productJpaRepository.save(product);

        // Publish event after successful save
        eventPublisher.publishEvent(
                new ProductChangedEvent(savedProduct.getId(), ProductOperation.CREATE)
        );

        log.info("Product created with ID: {}", savedProduct.getId());
        return savedProduct;
    }

    /**
     * Update an existing product
     */
    @Transactional
    public Product update(Long id, ProductUpdateRequest request) {
        log.info("Updating product with ID: {}", id);

        Product product = productJpaRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.getDescription() != null) {
            product.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            product.setPrice(request.getPrice().doubleValue());
        }
        if (request.getCategory() != null) {
            product.setCategory(request.getCategory());
        }
        if (request.getTags() != null) {
            product.setTagsFromList(request.getTags());
        }

        Product updatedProduct = productJpaRepository.save(product);

        // Publish event after successful update
        eventPublisher.publishEvent(
                new ProductChangedEvent(updatedProduct.getId(), ProductOperation.UPDATE)
        );

        log.info("Product updated: {}", id);
        return updatedProduct;
    }

    /**
     * Delete a product
     */
    @Transactional
    public void delete(Long id) {
        log.info("Deleting product with ID: {}", id);

        if (!productJpaRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }

        productJpaRepository.deleteById(id);

        // Publish event after successful delete
        eventPublisher.publishEvent(
                new ProductChangedEvent(id, ProductOperation.DELETE)
        );

        log.info("Product deleted: {}", id);
    }
}
