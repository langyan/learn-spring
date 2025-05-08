package com.lin.spring.micrometer.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import com.lin.spring.micrometer.service.PaymentService;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/orders")
public class OrderController {

// private final WebClient webClient;

    private final PaymentService paymentService;
    // public OrderController(WebClient.Builder builder) {
    //     this.webClient = builder.baseUrl("http://inventory-service").build();
    // }

    public OrderController(PaymentService paymentService) {
        this.paymentService=paymentService;
    }

    @GetMapping("/{id}")
    public Mono<String> getOrder(@PathVariable String id) {

        return Mono.just(id).map(orderId->{
            paymentService.processPayment(orderId);
            return orderId;
        });
        // return webClient.get()
        //         .uri("/inventory/" + id)
        //         .retrieve()
        //         .bodyToMono(String.class)
        //         .map(inv -> "Order: " + id + ", Inventory: " + inv);
    }
}