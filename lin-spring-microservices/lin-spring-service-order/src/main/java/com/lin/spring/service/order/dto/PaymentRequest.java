package com.lin.spring.service.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {

    private Long orderId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private String cardNumber;
    private String cardHolder;
    private String expiryDate;
    private String cvv;
}
