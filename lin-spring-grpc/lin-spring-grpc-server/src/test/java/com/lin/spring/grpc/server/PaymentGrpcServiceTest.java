package com.lin.spring.grpc.server;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.lin.spring.grpc.proto.ListPaymentsRequest;
import com.lin.spring.grpc.proto.PaymentRequest;
import com.lin.spring.grpc.proto.PaymentResponse;
import com.lin.spring.grpc.proto.PaymentServiceGrpc;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 用 InProcess 传输直接测 PaymentGrpcService（无需真实端口/Spring 上下文）。
 */
class PaymentGrpcServiceTest {

    private static final String SERVER_NAME = "payment-test";

    private io.grpc.Server server;
    private ManagedChannel channel;
    private PaymentServiceGrpc.PaymentServiceBlockingStub stub;

    @BeforeEach
    void setUp() throws Exception {
        server = InProcessServerBuilder.forName(SERVER_NAME)
                .directExecutor()
                .addService(new PaymentGrpcService())
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(SERVER_NAME)
                .directExecutor()
                .build();
        stub = PaymentServiceGrpc.newBlockingStub(channel);
    }

    @AfterEach
    void tearDown() {
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void getPayment_returnsSeededPayment() {
        PaymentResponse response = stub.getPayment(
                PaymentRequest.newBuilder().setPaymentId("PAY-001").build());

        assertThat(response.getPaymentId()).isEqualTo("PAY-001");
        assertThat(response.getUserId()).isEqualTo("USER-1");
        assertThat(response.getAmount()).isEqualTo(129.99);
        assertThat(response.getCurrency()).isEqualTo("CNY");
        assertThat(response.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void getPayment_blankId_failsWithInvalidArgument() {
        assertThatThrownBy(() -> stub.getPayment(
                PaymentRequest.newBuilder().setPaymentId(" ").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e ->
                        assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.INVALID_ARGUMENT));
    }

    @Test
    void getPayment_unknownId_failsWithNotFound() {
        assertThatThrownBy(() -> stub.getPayment(
                PaymentRequest.newBuilder().setPaymentId("PAY-999").build()))
                .isInstanceOfSatisfying(StatusRuntimeException.class, e -> {
                    assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.NOT_FOUND);
                    assertThat(e.getStatus().getDescription()).contains("PAY-999");
                });
    }

    @Test
    void listPayments_streamsAllPaymentsOfUserInOrder() {
        List<PaymentResponse> payments = collect(
                ListPaymentsRequest.newBuilder().setUserId("USER-1").build());

        assertThat(payments)
                .extracting(PaymentResponse::getPaymentId)
                .containsExactly("PAY-001", "PAY-002", "PAY-003");
    }

    @Test
    void listPayments_respectsPageSize() {
        List<PaymentResponse> payments = collect(
                ListPaymentsRequest.newBuilder().setUserId("USER-1").setPageSize(2).build());

        assertThat(payments)
                .extracting(PaymentResponse::getPaymentId)
                .containsExactly("PAY-001", "PAY-002");
    }

    @Test
    void listPayments_unknownUser_returnsEmptyStream() {
        List<PaymentResponse> payments = collect(
                ListPaymentsRequest.newBuilder().setUserId("NOBODY").build());

        assertThat(payments).isEmpty();
    }

    private List<PaymentResponse> collect(ListPaymentsRequest request) {
        List<PaymentResponse> payments = new ArrayList<>();
        stub.listPayments(request).forEachRemaining(payments::add);
        return payments;
    }
}
