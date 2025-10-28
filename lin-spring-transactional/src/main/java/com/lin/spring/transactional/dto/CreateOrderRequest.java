package com.lin.spring.transactional.dto;

public class CreateOrderRequest {
    private String customerName;
    private Double amount;

    // getters and setters
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public Double getAmount() { return amount; }
    public void setAmount(Double amount) { this.amount = amount; }
}
