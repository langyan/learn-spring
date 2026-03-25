package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class ProductService {

    @Autowired
    private ProductRepository repository;

    public Product create(Product product) {
        product.setCreatedAt(LocalDateTime.now());
        return repository.save(product);
    }

    public Optional<Product> getById(String id) {
        return repository.findById(id);
    }

    public List<Product> getAll() {
        return (List<Product>) repository.findAll();
    }

    public Product update(String id, Product product) {
        product.setId(id);
        Product existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setCreatedAt(existing.getCreatedAt());
        return repository.save(product);
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    public Page<Product> search(String keyword, Pageable pageable) {
        return repository.findByNameContainingOrDescriptionContaining(
                keyword, keyword, pageable);
    }

    public Page<Product> advancedSearch(String category, Double minPrice, Double maxPrice, Pageable pageable) {
        if (category != null && maxPrice != null) {
            return repository.findByCategoryAndPriceLessThanEqual(category, maxPrice, pageable);
        } else if (category != null) {
            return repository.findByCategory(category, pageable);
        } else if (minPrice != null && maxPrice != null) {
            return repository.findByPriceBetween(minPrice, maxPrice, pageable);
        }
        return repository.findAll(pageable);
    }
}
