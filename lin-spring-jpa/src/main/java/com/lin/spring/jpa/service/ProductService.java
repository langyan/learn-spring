package com.lin.spring.jpa.service;

import java.util.List;

import com.lin.spring.jpa.repository.ProductRepository;

import org.springframework.stereotype.Service;

import com.lin.spring.jpa.model.Product;

@Service
public class ProductService {
    private final ProductRepository repository;


    public ProductService(ProductRepository repository) {
        this.repository = repository;

        
    }

    public List<Product> getOptimizedProducts(String category) {
        return repository.findOptimizedByCategory(category);
    }
}