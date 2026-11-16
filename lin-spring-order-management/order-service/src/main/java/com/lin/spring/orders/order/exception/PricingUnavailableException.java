package com.lin.spring.orders.order.exception;

/** 无法获得可信价格（库存服务不可用/商品不存在）：拒绝下单而非接受一个无价订单。 */
public class PricingUnavailableException extends RuntimeException {
    public PricingUnavailableException(String message) {
        super(message);
    }
}
