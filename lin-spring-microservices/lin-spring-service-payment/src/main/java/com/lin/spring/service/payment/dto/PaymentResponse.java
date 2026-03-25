package com.lin.spring.service.payment.dto;

import com.lin.spring.service.payment.model.Payment;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Payment Response DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    private Long id;
    private String transactionId;
    private Long orderId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethod;
    private Payment.PaymentStatus status;
    private String failureReason;
    private LocalDateTime createdAt;

    public static PaymentResponse fromEntity(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getTransactionId(),
                payment.getOrderId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getPaymentMethod(),
                payment.getStatus(),
                payment.getFailureReason(),
                payment.getCreatedAt()
        );
    }
}
