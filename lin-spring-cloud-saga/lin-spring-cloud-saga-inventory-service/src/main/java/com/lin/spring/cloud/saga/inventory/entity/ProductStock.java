package com.lin.spring.cloud.saga.inventory.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_stocks")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String productId;

    @Column(nullable = false)
    private Integer stockQuantity;

    @Column(nullable = false)
    private Integer reservedQuantity;

    @PrePersist
    protected void onCreate() {
        if (stockQuantity == null) {
            stockQuantity = 100; // 默认库存
        }
        if (reservedQuantity == null) {
            reservedQuantity = 0;
        }
    }
}