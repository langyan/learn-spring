package com.lin.spring.moviebooking.payment.controller;

import com.lin.spring.moviebooking.common.dto.PaymentCommand;
import com.lin.spring.moviebooking.common.dto.PaymentInitiationResponse;
import com.lin.spring.moviebooking.common.dto.PaymentStatusResponse;
import com.lin.spring.moviebooking.payment.service.GatewaySimulator;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final GatewaySimulator gateway;

    @PostMapping("/intents")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentInitiationResponse createIntent(@Valid @RequestBody PaymentCommand command) {
        return gateway.createIntent(command);
    }

    @GetMapping("/{gatewayRef}/status")
    public PaymentStatusResponse status(@PathVariable String gatewayRef) {
        return gateway.status(gatewayRef);
    }
}
