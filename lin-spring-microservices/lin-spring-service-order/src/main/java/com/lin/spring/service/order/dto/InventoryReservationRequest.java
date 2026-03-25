package com.lin.spring.service.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservationRequest {

    private String productCode;
    private Integer quantity;
    private Long orderId;
}
