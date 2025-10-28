package com.lin.spring.cloud.saga.common.enums;

public enum SagaStatus {
    PENDING,
    ORDER_CREATED,
    PAYMENT_PROCESSED,
    INVENTORY_RESERVED,
    SUCCESS,
    FAILED,
    COMPENSATING,
    COMPENSATED
}