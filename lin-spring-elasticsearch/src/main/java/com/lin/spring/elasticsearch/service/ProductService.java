package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
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

    public Optional<Product> getById(Long id) {
        return repository.findById(id);
    }

    public List<Product> getAll() {
        return (List<Product>) repository.findAll();
    }

    public Product update(Long id, Product product) {
        product.setId(id);
        Product existing = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));
        product.setCreatedAt(existing.getCreatedAt());
        return repository.save(product);
    }

    public void delete(Long id) {
        repository.deleteById(id);
    }

    public Page<Product> search(String keyword, Pageable pageable) {
        List<Product> products = repository.searchByNameOrDescription(keyword);
        return createPage(products, pageable);
    }

    public Page<Product> advancedSearch(String category, Double minPrice, Double maxPrice, Pageable pageable) {
        List<Product> products;
        if (category != null && maxPrice != null) {
            products = repository.findByCategoryAndPriceLessThanEqual(category, maxPrice);
        } else if (category != null) {
            products = repository.findByCategory(category);
        } else if (minPrice != null && maxPrice != null) {
            products = repository.findByPriceBetween(minPrice, maxPrice);
        } else {
            products = repository.findAll();
        }
        return createPage(products, pageable);
    }

    private Page<Product> createPage(List<Product> products, Pageable pageable) {
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), products.size());
        List<Product> pageContent = start < products.size() ?
                products.subList(start, end) : List.of();
        return new org.springframework.data.domain.PageImpl<>(pageContent, pageable, products.size());
    }
}
