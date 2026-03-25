package com.lin.spring.service.order.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShippingResponse {

    private Long id;
    private String trackingNumber;
    private Long orderId;
    private String status;
}
