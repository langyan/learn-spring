package com.lin.spring.cloud.saga.common.enums;

public enum PaymentStatus {
    PENDING,      // 待处理
    COMPLETED,    // 已完成
    REFUNDED,     // 已退款
    FAILED        // 已失败
}