package com.lin.spring.orders.payment.provider;

/** 渠道未在时限内响应：结果未知（绝不等于失败）。 */
public class ProviderTimeoutException extends Exception {
    public ProviderTimeoutException(String message) {
        super(message);
    }
}
