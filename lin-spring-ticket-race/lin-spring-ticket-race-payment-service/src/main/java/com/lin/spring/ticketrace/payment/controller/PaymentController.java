package com.lin.spring.ticketrace.payment.controller;

import com.lin.spring.ticketrace.common.dto.PaymentCommand;
import com.lin.spring.ticketrace.common.dto.PaymentResult;
import com.lin.spring.ticketrace.payment.service.PaymentSimulatorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentSimulatorService paymentSimulatorService;

    @PostMapping("/simulate")
    public PaymentResult simulate(@Valid @RequestBody PaymentCommand command) {
        return paymentSimulatorService.process(command);
    }
}
