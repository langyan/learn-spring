package com.lin.spring.service.order.service;

import com.lin.spring.service.order.dto.*;
import com.lin.spring.service.order.model.Order;
import com.lin.spring.service.order.model.OrderItem;
import com.lin.spring.service.order.repository.OrderRepository;
import com.lin.spring.service.order.service.client.InventoryServiceClient;
import com.lin.spring.service.order.service.client.PaymentServiceClient;
import com.lin.spring.service.order.service.client.ShippingServiceClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentServiceClient paymentServiceClient;
    private final InventoryServiceClient inventoryServiceClient;
    private final ShippingServiceClient shippingServiceClient;

    /**
     * Create order with Saga orchestration
     */
    @Transactional
    @CircuitBreaker(name = "orderCreation", fallbackMethod = "createOrderFallback")
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}", request.getUserId());

        // Create order
        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setStatus(Order.OrderStatus.PENDING);

        // Calculate total amount
        BigDecimal totalAmount = BigDecimal.ZERO;

        // Create order items
        for (OrderItemRequest itemRequest : request.getItems()) {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductCode(itemRequest.getProductCode());
            item.setProductName(itemRequest.getProductName());
            item.setQuantity(itemRequest.getQuantity());
            item.setPrice(itemRequest.getPrice());
            item.calculateSubtotal();
            order.getItems().add(item);
            totalAmount = totalAmount.add(item.getSubtotal());
        }

        order.setTotalAmount(totalAmount);
        orderRepository.save(order);

        log.info("Order created with number: {}", order.getOrderNumber());

        // Execute Saga: Reserve Inventory
        boolean inventoryReserved = reserveInventory(order);
        if (!inventoryReserved) {
            order.setStatus(Order.OrderStatus.INVENTORY_FAILED);
            orderRepository.save(order);
            throw new RuntimeException("Inventory reservation failed");
        }

        // Execute Saga: Process Payment
        boolean paymentProcessed = processPayment(order);
        if (!paymentProcessed) {
            // Compensating transaction: Release inventory
            releaseInventory(order);
            order.setStatus(Order.OrderStatus.PAYMENT_FAILED);
            orderRepository.save(order);
            throw new RuntimeException("Payment processing failed");
        }

        // Confirm inventory reservation
        confirmInventory(order);

        // Execute Saga: Create Shipment
        boolean shipped = createShipment(order);
        if (!shipped) {
            order.setStatus(Order.OrderStatus.SHIPPING_PROCESSING);
            orderRepository.save(order);
            throw new RuntimeException("Shipping creation failed");
        }

        order.setStatus(Order.OrderStatus.COMPLETED);
        orderRepository.save(order);

        log.info("Order completed: {}", order.getOrderNumber());

        return toResponse(order);
    }

    /**
     * Reserve inventory for order items
     */
    private boolean reserveInventory(Order order) {
        log.info("Reserving inventory for order: {}", order.getOrderNumber());

        try {
            for (OrderItem item : order.getItems()) {
                InventoryReservationRequest request = new InventoryReservationRequest();
                request.setProductCode(item.getProductCode());
                request.setQuantity(item.getQuantity());
                request.setOrderId(order.getId());

                Boolean reserved = inventoryServiceClient.reserveStock(request);
                if (reserved == null || !reserved) {
                    log.error("Failed to reserve inventory for product: {}", item.getProductCode());
                    return false;
                }
            }
            order.setStatus(Order.OrderStatus.INVENTORY_RESERVED);
            return true;
        } catch (Exception e) {
            log.error("Error reserving inventory", e);
            return false;
        }
    }

    /**
     * Confirm inventory reservation
     */
    private void confirmInventory(Order order) {
        log.info("Confirming inventory for order: {}", order.getOrderNumber());

        for (OrderItem item : order.getItems()) {
            try {
                inventoryServiceClient.confirmReservation(item.getProductCode(), item.getQuantity());
            } catch (Exception e) {
                log.error("Error confirming inventory for product: {}", item.getProductCode(), e);
            }
        }
    }

    /**
     * Release inventory (compensating transaction)
     */
    private void releaseInventory(Order order) {
        log.info("Releasing inventory for order: {}", order.getOrderNumber());

        for (OrderItem item : order.getItems()) {
            try {
                inventoryServiceClient.releaseReservation(item.getProductCode(), item.getQuantity());
            } catch (Exception e) {
                log.error("Error releasing inventory for product: {}", item.getProductCode(), e);
            }
        }
    }

    /**
     * Process payment for order
     */
    private boolean processPayment(Order order) {
        log.info("Processing payment for order: {}", order.getOrderNumber());

        try {
            order.setStatus(Order.OrderStatus.PAYMENT_PROCESSING);

            PaymentRequest request = new PaymentRequest();
            request.setOrderId(order.getId());
            request.setAmount(order.getTotalAmount());
            request.setCurrency("USD");
            request.setPaymentMethod("CREDIT_CARD");
            request.setCardNumber("4111111111111111");
            request.setCardHolder("Customer");
            request.setExpiryDate("12/25");
            request.setCvv("123");

            PaymentResponse response = paymentServiceClient.processPayment(request);

            if (response != null && "COMPLETED".equals(response.getStatus())) {
                order.setPaymentTransactionId(response.getTransactionId());
                order.setStatus(Order.OrderStatus.PAYMENT_COMPLETED);
                log.info("Payment completed for order: {}", order.getOrderNumber());
                return true;
            } else if (response != null && "PENDING".equals(response.getStatus())) {
                log.warn("Payment pending for order: {}", order.getOrderNumber());
                return false;
            } else {
                log.error("Payment failed for order: {}", order.getOrderNumber());
                return false;
            }
        } catch (Exception e) {
            log.error("Error processing payment", e);
            return false;
        }
    }

    /**
     * Create shipment for order
     */
    private boolean createShipment(Order order) {
        log.info("Creating shipment for order: {}", order.getOrderNumber());

        try {
            ShippingResponse response = shippingServiceClient.shipOrder(order.getId());

            if (response != null && response.getTrackingNumber() != null) {
                order.setShippingTrackingNumber(response.getTrackingNumber());
                order.setStatus(Order.OrderStatus.SHIPPED);
                log.info("Shipment created for order: {}", order.getOrderNumber());
                return true;
            } else {
                log.error("Shipment creation failed for order: {}", order.getOrderNumber());
                return false;
            }
        } catch (Exception e) {
            log.error("Error creating shipment", e);
            return false;
        }
    }

    /**
     * Fallback method for order creation
     */
    public OrderResponse createOrderFallback(CreateOrderRequest request, Throwable throwable) {
        log.error("Order creation fallback triggered for user: {}", request.getUserId(), throwable);
        OrderResponse response = new OrderResponse();
        response.setStatus(Order.OrderStatus.PENDING);
        return response;
    }

    /**
     * Get order by ID
     */
    public OrderResponse getOrderById(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return toResponse(order);
    }

    /**
     * Get order by order number
     */
    public OrderResponse getOrderByOrderNumber(String orderNumber) {
        Order order = orderRepository.findByOrderNumber(orderNumber)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        return toResponse(order);
    }

    private OrderResponse toResponse(Order order) {
        OrderResponse response = new OrderResponse();
        response.setId(order.getId());
        response.setOrderNumber(order.getOrderNumber());
        response.setUserId(order.getUserId());
        response.setStatus(order.getStatus());
        response.setTotalAmount(order.getTotalAmount());
        response.setPaymentTransactionId(order.getPaymentTransactionId());
        response.setShippingTrackingNumber(order.getShippingTrackingNumber());
        response.setCreatedAt(order.getCreatedAt());
        response.setUpdatedAt(order.getUpdatedAt());

        response.setItems(order.getItems().stream()
                .map(item -> new OrderItemResponse(
                        item.getId(),
                        item.getProductCode(),
                        item.getProductName(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getSubtotal()
                ))
                .collect(Collectors.toList()));

        return response;
    }
}
