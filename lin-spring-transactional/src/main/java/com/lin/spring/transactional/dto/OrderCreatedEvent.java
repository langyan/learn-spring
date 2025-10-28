package com.lin.spring.transactional.dto;

public class OrderCreatedEvent {
    private final Long orderId;
    private final String customerName;
    private final Double amount;

    public OrderCreatedEvent(Long orderId, String customerName, Double amount) {
        this.orderId = orderId;
        this.customerName = customerName;
        this.amount = amount;
    }

    // getters
    public Long getOrderId() { return orderId; }
    public String getCustomerName() { return customerName; }
    public Double getAmount() { return amount; }
}
