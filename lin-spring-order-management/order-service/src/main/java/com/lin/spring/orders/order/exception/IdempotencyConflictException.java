package com.lin.spring.orders.order.exception;

/** 同一幂等键被用于不同请求负载（409）：重复键不得静默返回无关结果。 */
public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException(String message) {
        super(message);
    }
}
