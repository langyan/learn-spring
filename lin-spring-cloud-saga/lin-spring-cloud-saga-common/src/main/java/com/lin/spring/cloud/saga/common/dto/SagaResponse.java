package com.lin.spring.cloud.saga.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SagaResponse {
    private String sagaId;
    private String status;
    private String orderId;
    private String paymentId;
    private String inventoryId;
    private String errorMessage;
    private LocalDateTime createdAt;
}