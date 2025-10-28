package com.lin.spring.cloud.saga.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaRequest {
    private String userId;
    private String productId;
    private Integer quantity;
    private BigDecimal amount;
}