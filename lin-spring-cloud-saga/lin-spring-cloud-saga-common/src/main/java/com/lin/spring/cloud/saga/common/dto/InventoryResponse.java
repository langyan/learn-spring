package com.lin.spring.cloud.saga.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryResponse {
    private String inventoryId;
    private String orderId;
    private String productId;
    private Integer quantity;
    private String status;
    private LocalDateTime createdAt;
}