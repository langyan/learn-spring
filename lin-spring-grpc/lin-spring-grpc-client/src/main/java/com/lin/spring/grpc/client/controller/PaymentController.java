package com.lin.spring.grpc.client.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lin.spring.grpc.client.service.PaymentClientService;
import com.lin.spring.grpc.client.web.GrpcExceptionMapper;
import com.lin.spring.grpc.proto.PaymentResponse;

import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST 网关：对外是普通 HTTP API，对内走 gRPC 调用 grpc-server。
 */
@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentClientService paymentClientService;
    private final GrpcExceptionMapper grpcExceptionMapper;

    /** GET /api/payments/{paymentId} -> 一元 RPC GetPayment */
    @GetMapping("/{paymentId}")
    public ResponseEntity<?> getPayment(@PathVariable String paymentId) {
        try {
            PaymentResponse response = paymentClientService.getPayment(paymentId);
            return ResponseEntity.ok(toMap(response));
        } catch (StatusRuntimeException e) {
            log.warn("gRPC error while getting payment {}: {}", paymentId, e.getStatus());
            return grpcExceptionMapper.toHttpResponse(e);
        }
    }

    /** GET /api/payments?userId=USER-1&pageSize=10 -> 服务器流式 RPC ListPayments */
    @GetMapping
    public ResponseEntity<?> listPayments(
            @RequestParam String userId,
            @RequestParam(defaultValue = "10") int pageSize) {
        try {
            List<PaymentResponse> responses = paymentClientService.listPayments(userId, pageSize);
            return ResponseEntity.ok(responses.stream().map(this::toMap).toList());
        } catch (StatusRuntimeException e) {
            log.warn("gRPC error while listing payments for {}: {}", userId, e.getStatus());
            return grpcExceptionMapper.toHttpResponse(e);
        }
    }

    private Map<String, Object> toMap(PaymentResponse p) {
        return Map.of(
                "paymentId", p.getPaymentId(),
                "userId", p.getUserId(),
                "amount", p.getAmount(),
                "currency", p.getCurrency(),
                "status", p.getStatus(),
                "createdAt", p.getCreatedAt());
    }
}
