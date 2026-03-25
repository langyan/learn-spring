package com.lin.spring.service.payment.service;

import com.lin.spring.service.payment.dto.PaymentRequest;
import com.lin.spring.service.payment.dto.PaymentResponse;
import com.lin.spring.service.payment.model.Payment;
import com.lin.spring.service.payment.repository.PaymentRepository;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Random;

/**
 * Payment Service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final Random random = new Random();

    /**
     * Process payment with circuit breaker
     */
    @CircuitBreaker(name = "externalPaymentGateway", fallbackMethod = "processPaymentFallback")
    public PaymentResponse processPayment(PaymentRequest request) {
        log.info("Processing payment for order: {}", request.getOrderId());

        // Simulate external payment gateway call
        // In production, this would call a real payment gateway like Stripe, PayPal, etc.
        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setPaymentMethod(request.getPaymentMethod());

        // Simulate payment processing (90% success rate)
        boolean paymentSuccess = random.nextInt(100) < 90;

        if (paymentSuccess) {
            payment.setStatus(Payment.PaymentStatus.COMPLETED);
            log.info("Payment completed for order: {}", request.getOrderId());
        } else {
            payment.setStatus(Payment.PaymentStatus.FAILED);
            payment.setFailureReason("Insufficient funds");
            log.warn("Payment failed for order: {}", request.getOrderId());
        }

        paymentRepository.save(payment);

        return PaymentResponse.fromEntity(payment);
    }

    /**
     * Fallback method when external payment gateway is unavailable
     */
    public PaymentResponse processPaymentFallback(PaymentRequest request, Throwable throwable) {
        log.error("Payment gateway unavailable, using fallback for order: {}", request.getOrderId(), throwable);

        // Create a pending payment to be processed later
        Payment payment = new Payment();
        payment.setOrderId(request.getOrderId());
        payment.setAmount(request.getAmount());
        payment.setCurrency(request.getCurrency());
        payment.setPaymentMethod(request.getPaymentMethod());
        payment.setStatus(Payment.PaymentStatus.PENDING);
        payment.setFailureReason("Payment gateway unavailable - will retry");

        paymentRepository.save(payment);

        return PaymentResponse.fromEntity(payment);
    }

    /**
     * Get payment by ID
     */
    public PaymentResponse getPaymentById(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        return PaymentResponse.fromEntity(payment);
    }

    /**
     * Get payment by transaction ID
     */
    public PaymentResponse getPaymentByTransactionId(String transactionId) {
        Payment payment = paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        return PaymentResponse.fromEntity(payment);
    }

    /**
     * Get payment by order ID
     */
    public PaymentResponse getPaymentByOrderId(Long orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));
        return PaymentResponse.fromEntity(payment);
    }

    /**
     * Refund payment
     */
    public PaymentResponse refundPayment(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new RuntimeException("Payment not found"));

        if (payment.getStatus() != Payment.PaymentStatus.COMPLETED) {
            throw new RuntimeException("Can only refund completed payments");
        }

        payment.setStatus(Payment.PaymentStatus.REFUNDED);
        paymentRepository.save(payment);

        log.info("Payment refunded: {}", payment.getTransactionId());

        return PaymentResponse.fromEntity(payment);
    }
}
