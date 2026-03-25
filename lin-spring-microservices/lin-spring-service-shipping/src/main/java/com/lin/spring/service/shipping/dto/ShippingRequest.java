package com.lin.spring.service.shipping.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShippingRequest {

    @NotNull(message = "Order ID is required")
    private Long orderId;

    @NotBlank(message = "Recipient name is required")
    private String recipientName;

    @NotBlank(message = "Address is required")
    private String recipientAddress;

    @NotBlank(message = "City is required")
    private String recipientCity;

    @NotBlank(message = "Postal code is required")
    private String recipientPostalCode;

    @NotBlank(message = "Country is required")
    private String recipientCountry;

    private String carrier;
}
