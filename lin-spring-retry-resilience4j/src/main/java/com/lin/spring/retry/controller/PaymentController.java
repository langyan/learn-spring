package com.lin.spring.retry.controller;

import org.springframework.web.bind.annotation.RestController;

import com.lin.spring.retry.service.PaymentService;

import lombok.RequiredArgsConstructor;

import java.util.concurrent.CompletableFuture;

import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;


@RestController
@RequestMapping("/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @GetMapping("/{transactionId}")
    public ResponseEntity<String> getMethodName(@PathVariable String transactionId) {
        //
    
        CompletableFuture.runAsync(()->paymentService.processPayment(transactionId));
        return ResponseEntity.ok(transactionId);
    }
    
    
}
