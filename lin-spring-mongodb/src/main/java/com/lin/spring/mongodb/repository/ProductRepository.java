package com.lin.spring.mongodb.repository;

import com.lin.spring.mongodb.entity.Product;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

public interface ProductRepository extends ReactiveMongoRepository<Product, String> {
    Flux<Product> findByPriceGreaterThan(double price);
}
