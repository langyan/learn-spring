package com.lin.spring.cloud.saga.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRequest {
    @NotBlank(message = "订单ID不能为空")
    private String orderId;

    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;
}