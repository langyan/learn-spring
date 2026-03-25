package com.lin.spring.service.order.dto;

import com.lin.spring.service.order.model.Order;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {

    private Long id;
    private String orderNumber;
    private Long userId;
    private Order.OrderStatus status;
    private BigDecimal totalAmount;
    private List<OrderItemResponse> items;
    private String paymentTransactionId;
    private String shippingTrackingNumber;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
