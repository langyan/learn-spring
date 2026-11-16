package com.lin.spring.orders.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TokenRequest(
        @NotNull @Min(1) @Max(1_000_000L) Long customerId,
        @NotBlank @jakarta.validation.constraints.Size(max = 100) String name) {
}
