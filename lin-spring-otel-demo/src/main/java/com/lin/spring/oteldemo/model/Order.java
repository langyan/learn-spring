package com.lin.spring.oteldemo.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    private String id;
    private String itemName;
    private String category;
    private Double amount;
    private OrderStatus status;
    private LocalDateTime createdAt;

    public enum OrderStatus {
        ACTIVE,
        COMPLETED,
        CANCELLED
    }
}
