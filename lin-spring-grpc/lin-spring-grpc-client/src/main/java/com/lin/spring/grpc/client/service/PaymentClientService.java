package com.lin.spring.grpc.client.service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.lin.spring.grpc.proto.ListPaymentsRequest;
import com.lin.spring.grpc.proto.PaymentRequest;
import com.lin.spring.grpc.proto.PaymentResponse;
import com.lin.spring.grpc.proto.PaymentServiceGrpc;

import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;

/**
 * gRPC 客户端封装：由 grpc-client-spring-boot-starter 注入 BlockingStub，
 * 所有调用都带 2 秒 Deadline，避免下游挂起拖垮网关。
 */
@Slf4j
@Service
public class PaymentClientService {

    private static final long DEADLINE_MILLIS = 2_000;

    @GrpcClient("payment-service")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub;

    /** 一元调用：deadline 到期抛 StatusRuntimeException(DEADLINE_EXCEEDED)。 */
    public PaymentResponse getPayment(String paymentId) {
        log.info("Requesting payment: {}", paymentId);
        PaymentRequest request = PaymentRequest.newBuilder()
                .setPaymentId(paymentId)
                .build();
        return paymentStub
                .withDeadlineAfter(DEADLINE_MILLIS, TimeUnit.MILLISECONDS)
                .getPayment(request);
    }

    /** 服务器流式调用：客户端拿到 Iterator，逐条消费。 */
    public List<PaymentResponse> listPayments(String userId, int pageSize) {
        log.info("Listing payments for user: {}", userId);
        ListPaymentsRequest request = ListPaymentsRequest.newBuilder()
                .setUserId(userId)
                .setPageSize(pageSize)
                .build();
        Iterator<PaymentResponse> iterator = paymentStub
                .withDeadlineAfter(DEADLINE_MILLIS, TimeUnit.MILLISECONDS)
                .listPayments(request);
        List<PaymentResponse> payments = new ArrayList<>();
        iterator.forEachRemaining(payments::add);
        return payments;
    }
}
