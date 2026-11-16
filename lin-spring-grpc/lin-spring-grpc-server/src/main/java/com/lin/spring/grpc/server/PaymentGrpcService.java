package com.lin.spring.grpc.server;

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import com.lin.spring.grpc.proto.ListPaymentsRequest;
import com.lin.spring.grpc.proto.PaymentServiceGrpc;
import com.lin.spring.grpc.proto.PaymentRequest;
import com.lin.spring.grpc.proto.PaymentResponse;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;

/**
 * PaymentService 的 gRPC 实现。
 *
 * <p>每个方法的固定模式：验证请求 -> 干活 -> onNext（一元一次/流式多次）-> onCompleted；
 * 失败时改为 onError(Status...asRuntimeException())。</p>
 *
 * <p>演示数据：PAY-001..PAY-005（USER-1 / USER-2）。以 "SLOW-" 开头的 paymentId 会人为
 * 沉睡 5 秒，用于触发客户端 2 秒 Deadline，最终客户端收到 DEADLINE_EXCEEDED。</p>
 */
@Slf4j
@Service
@GrpcService
public class PaymentGrpcService extends PaymentServiceGrpc.PaymentServiceImplBase {

    private static final long SLOW_DELAY_MILLIS = 5_000;

    private final Map<String, PaymentResponse> payments = new ConcurrentHashMap<>();

    public PaymentGrpcService() {
        // 启动时播种种子数据，便于端到端验证
        seed("PAY-001", "USER-1", 129.99, "CNY", "COMPLETED", "2026-11-01T10:15:30Z");
        seed("PAY-002", "USER-1", 59.00, "CNY", "PENDING", "2026-11-03T08:00:00Z");
        seed("PAY-003", "USER-1", 899.50, "USD", "COMPLETED", "2026-11-05T14:22:10Z");
        seed("PAY-004", "USER-2", 42.30, "USD", "FAILED", "2026-11-06T19:45:00Z");
        seed("PAY-005", "USER-2", 15.00, "CNY", "REFUNDED", "2026-11-10T11:05:59Z");
    }

    private void seed(String paymentId, String userId, double amount,
                      String currency, String status, String createdAt) {
        payments.put(paymentId, PaymentResponse.newBuilder()
                .setPaymentId(paymentId)
                .setUserId(userId)
                .setAmount(amount)
                .setCurrency(currency)
                .setStatus(status)
                .setCreatedAt(createdAt)
                .build());
    }

    /** 一元 RPC：一次请求一次响应。 */
    @Override
    public void getPayment(PaymentRequest request,
                           StreamObserver<PaymentResponse> responseObserver) {
        String paymentId = request.getPaymentId();
        if (paymentId == null || paymentId.isBlank()) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("Payment ID must not be empty")
                    .asRuntimeException());
            return;
        }
        if (paymentId.startsWith("SLOW-")) {
            // Deadline 演示：故意慢于客户端的 2s 截止时间
            log.info("Slow payment requested, sleeping {}ms: {}", SLOW_DELAY_MILLIS, paymentId);
            try {
                TimeUnit.MILLISECONDS.sleep(SLOW_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                responseObserver.onError(Status.CANCELLED
                        .withDescription("Interrupted while sleeping")
                        .asRuntimeException());
                return;
            }
        }
        PaymentResponse payment = payments.get(paymentId);
        if (payment == null) {
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription("Payment not found: " + paymentId)
                    .asRuntimeException());
            return;
        }
        responseObserver.onNext(payment);
        responseObserver.onCompleted();
    }

    /** 服务器流式 RPC：一次请求，多条 PaymentResponse 依次 onNext 推送。 */
    @Override
    public void listPayments(ListPaymentsRequest request,
                             StreamObserver<PaymentResponse> responseObserver) {
        String userId = request.getUserId();
        int pageSize = request.getPageSize() > 0 ? request.getPageSize() : 10;

        payments.values().stream()
                .filter(p -> p.getUserId().equals(userId))
                .sorted(Comparator.comparing(PaymentResponse::getPaymentId))
                .limit(pageSize)
                .forEach(responseObserver::onNext);

        responseObserver.onCompleted();
    }
}
