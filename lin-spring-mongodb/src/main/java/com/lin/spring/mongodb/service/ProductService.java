package com.lin.spring.mongodb.service;

import com.lin.spring.mongodb.entity.Product;
import com.lin.spring.mongodb.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

@Service
public class ProductService {
    @Autowired
    private ProductRepository repo;

    public Mono<Product> save(Product product) {
        return repo.save(product);
    }
    public Flux<Product> findAll() {
        return repo.findAll().delayElements(Duration.ofMillis(100)); // Simulate streaming
    }
    public Flux<Product> filterExpensive(double minPrice) {
        return repo.findByPriceGreaterThan(minPrice)
                .subscribeOn(Schedulers.boundedElastic());
    }
}