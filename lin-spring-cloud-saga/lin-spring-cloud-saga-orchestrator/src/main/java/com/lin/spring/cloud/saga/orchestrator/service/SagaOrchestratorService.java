package com.lin.spring.cloud.saga.orchestrator.service;

import com.lin.spring.cloud.saga.common.dto.*;
import com.lin.spring.cloud.saga.common.entity.SagaState;
import com.lin.spring.cloud.saga.common.enums.SagaStatus;
import com.lin.spring.cloud.saga.common.repository.SagaStateRepository;
import com.lin.spring.cloud.saga.orchestrator.client.InventoryClient;
import com.lin.spring.cloud.saga.orchestrator.client.OrderClient;
import com.lin.spring.cloud.saga.orchestrator.client.PaymentClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestratorService {

    private final OrderClient orderClient;
    private final PaymentClient paymentClient;
    private final InventoryClient inventoryClient;
    private final SagaStateRepository sagaStateRepository;

    @Transactional
    @Retryable(value = {Exception.class}, maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public SagaResponse executeSaga(SagaRequest request) {
        String sagaId = "SAGA_" + UUID.randomUUID().toString().substring(0, 8);
        log.info("Starting saga execution: {}", sagaId);

        // 初始化 Saga 状态
        SagaState sagaState = new SagaState();
        sagaState.setSagaId(sagaId);
        sagaState.setStatus(SagaStatus.PENDING);
        sagaStateRepository.save(sagaState);

        try {
            // 步骤1: 创建订单
            log.info("Step 1: Creating order for saga: {}", sagaId);
            CreateOrderRequest orderRequest = new CreateOrderRequest(
                request.getUserId(),
                request.getProductId(),
                request.getQuantity(),
                request.getAmount()
            );
            OrderResponse orderResponse = orderClient.createOrder(orderRequest);

            sagaState.setOrderId(orderResponse.getOrderId());
            sagaState.setStatus(SagaStatus.ORDER_CREATED);
            sagaStateRepository.save(sagaState);

            // 步骤2: 处理支付
            log.info("Step 2: Processing payment for saga: {}", sagaId);
            PaymentRequest paymentRequest = new PaymentRequest(
                orderResponse.getOrderId(),
                request.getUserId(),
                request.getAmount()
            );
            PaymentResponse paymentResponse = paymentClient.processPayment(paymentRequest);

            sagaState.setPaymentId(paymentResponse.getPaymentId());
            sagaState.setStatus(SagaStatus.PAYMENT_PROCESSED);
            sagaStateRepository.save(sagaState);

            // 步骤3: 预留库存
            log.info("Step 3: Reserving inventory for saga: {}", sagaId);
            InventoryRequest inventoryRequest = new InventoryRequest(
                orderResponse.getOrderId(),
                request.getProductId(),
                request.getQuantity()
            );
            InventoryResponse inventoryResponse = inventoryClient.reserveInventory(inventoryRequest);

            sagaState.setInventoryId(inventoryResponse.getInventoryId());
            sagaState.setStatus(SagaStatus.INVENTORY_RESERVED);
            sagaStateRepository.save(sagaState);

            // 完成 Saga
            log.info("Step 4: Completing saga: {}", sagaId);
            orderClient.completeOrder(orderResponse.getOrderId());
            inventoryClient.completeInventory(inventoryResponse.getInventoryId());

            sagaState.setStatus(SagaStatus.SUCCESS);
            sagaStateRepository.save(sagaState);

            log.info("Saga completed successfully: {}", sagaId);
            return createSagaResponse(sagaState, "Saga completed successfully");

        } catch (Exception e) {
            log.error("Saga execution failed: {}. Starting compensation...", sagaId, e);

            try {
                compensate(sagaState, e.getMessage());
            } catch (Exception compensationException) {
                log.error("Compensation failed for saga: {}", sagaId, compensationException);
                sagaState.setErrorMessage("Compensation failed: " + compensationException.getMessage());
            }

            sagaState.setStatus(SagaStatus.FAILED);
            sagaStateRepository.save(sagaState);

            return createSagaResponse(sagaState, "Saga failed: " + e.getMessage());
        }
    }

    private void compensate(SagaState sagaState, String errorMessage) {
        log.info("Starting compensation for saga: {}", sagaState.getSagaId());
        sagaState.setStatus(SagaStatus.COMPENSATING);
        sagaState.setErrorMessage(errorMessage);
        sagaStateRepository.save(sagaState);

        // 反向执行补偿操作
        try {
            // 步骤3: 释放库存
            if (sagaState.getInventoryId() != null) {
                log.info("Compensation step 1: Releasing inventory for saga: {}", sagaState.getSagaId());
                inventoryClient.releaseInventory(sagaState.getInventoryId());
            }

            // 步骤2: 退款
            if (sagaState.getPaymentId() != null) {
                log.info("Compensation step 2: Refunding payment for saga: {}", sagaState.getSagaId());
                paymentClient.refundPayment(sagaState.getPaymentId());
            }

            // 步骤1: 取消订单
            if (sagaState.getOrderId() != null) {
                log.info("Compensation step 3: Cancelling order for saga: {}", sagaState.getSagaId());
                orderClient.cancelOrder(sagaState.getOrderId());
            }

            sagaState.setStatus(SagaStatus.COMPENSATED);
            sagaStateRepository.save(sagaState);
            log.info("Compensation completed successfully for saga: {}", sagaState.getSagaId());

        } catch (Exception e) {
            log.error("Compensation failed for saga: {}", sagaState.getSagaId(), e);
            throw new RuntimeException("Compensation failed: " + e.getMessage());
        }
    }

    @Transactional
    public SagaResponse getSagaStatus(String sagaId) {
        log.info("Getting saga status: {}", sagaId);

        SagaState sagaState = sagaStateRepository.findBySagaId(sagaId)
                .orElseThrow(() -> new RuntimeException("Saga not found: " + sagaId));

        return createSagaResponse(sagaState, null);
    }

    private SagaResponse createSagaResponse(SagaState sagaState, String message) {
        return new SagaResponse(
            sagaState.getSagaId(),
            sagaState.getStatus().name(),
            sagaState.getOrderId(),
            sagaState.getPaymentId(),
            sagaState.getInventoryId(),
            message != null ? message : sagaState.getErrorMessage(),
            sagaState.getCreatedAt()
        );
    }
}