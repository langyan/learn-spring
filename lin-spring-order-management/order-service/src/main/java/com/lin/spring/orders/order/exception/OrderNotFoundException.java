package com.lin.spring.orders.order.exception;

/** 订单不存在或不属于当前客户（404：不泄露他人订单的存在性）。 */
public class OrderNotFoundException extends RuntimeException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
