package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.exception.ProductNotFoundException;
import com.lin.spring.elasticsearch.repository.ProductJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for product read operations (MySQL)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProductReadService {

    private final ProductJpaRepository productJpaRepository;

    /**
     * Get product by ID
     */
    public Product getById(Long id) {
        log.debug("Fetching product with ID: {}", id);
        return productJpaRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Get all products
     */
    public List<Product> findAll() {
        log.debug("Fetching all products");
        return productJpaRepository.findAll();
    }
}
