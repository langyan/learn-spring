package com.lin.spring.orders.inventory.service;

/** 库存不足：事务回滚（部分预留一并撤销），由调用方转写 INVENTORY_REJECTED 事件。 */
public class InsufficientStockException extends RuntimeException {

    private final long productId;

    public InsufficientStockException(long productId, String message) {
        super(message);
        this.productId = productId;
    }

    public long productId() {
        return productId;
    }
}
