package com.lin.spring.mongodb.controller;

import com.lin.spring.mongodb.entity.Product;
import com.lin.spring.mongodb.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    @Autowired
    private ProductService service;

    @PostMapping
    public Mono<ResponseEntity<Product>> create(@RequestBody Product product) {
        return service.save(product)
                .map(p -> ResponseEntity.status(HttpStatus.CREATED).body(p));
    }
    @GetMapping
    public Flux<Product> getAll() {
        return service.findAll();
    }
    @GetMapping("/expensive")
    public Flux<Product> getExpensive(@RequestParam double price) {
        return service.filterExpensive(price);
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Product>> streamData() {
        return service.findAll()
                .map(product -> ServerSentEvent.builder(product).build());
    }
}
