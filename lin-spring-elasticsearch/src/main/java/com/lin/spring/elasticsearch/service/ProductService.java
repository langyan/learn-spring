package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import com.lin.spring.elasticsearch.entity.SyncLog.SyncOperation;
import com.lin.spring.elasticsearch.repository.ProductElasticsearchRepository;
import com.lin.spring.elasticsearch.repository.ProductJpaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    @Autowired
    private ProductJpaRepository jpaRepository;

    @Autowired
    private ProductElasticsearchRepository esRepository;

    @Autowired
    private SyncService syncService;

    @Transactional
    public Product create(Product product) {
        Product saved = jpaRepository.save(product);
        syncService.syncToElasticsearch(saved, SyncOperation.CREATE);
        return saved;
    }

    @Transactional
    public Product update(Long id, Product product) {
        product.setId(id);
        Product existing = jpaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        Product updated = jpaRepository.save(product);
        syncService.syncToElasticsearch(updated, SyncOperation.UPDATE);
        return updated;
    }

    @Transactional
    public void delete(Long id) {
        Product product = jpaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        jpaRepository.deleteById(id);
        syncService.syncToElasticsearch(product, SyncOperation.DELETE);
    }

    public Optional<Product> getById(Long id) {
        return jpaRepository.findById(id);
    }

    public List<Product> getAll() {
        return jpaRepository.findAll();
    }

    public Page<ProductDocument> search(String keyword, Pageable pageable) {
        return esRepository.findByNameContainingOrDescriptionContaining(
                keyword, keyword, pageable);
    }

    public Page<ProductDocument> advancedSearch(String category, Double minPrice, Double maxPrice, Pageable pageable) {
        if (category != null && maxPrice != null) {
            return esRepository.findByCategoryAndPriceLessThanEqual(category, maxPrice, pageable);
        } else if (category != null) {
            return esRepository.findByCategory(category, pageable);
        } else if (minPrice != null && maxPrice != null) {
            return esRepository.findByPriceBetween(minPrice, maxPrice, pageable);
        }
        return esRepository.findAll(pageable);
    }
}
