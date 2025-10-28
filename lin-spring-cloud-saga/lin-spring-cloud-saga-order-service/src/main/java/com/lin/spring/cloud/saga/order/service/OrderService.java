package com.lin.spring.cloud.saga.order.service;

import com.lin.spring.cloud.saga.common.dto.CreateOrderRequest;
import com.lin.spring.cloud.saga.common.dto.OrderResponse;
import com.lin.spring.cloud.saga.common.enums.OrderStatus;
import com.lin.spring.cloud.saga.order.entity.Order;
import com.lin.spring.cloud.saga.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        log.info("Creating order for user: {}, product: {}", request.getUserId(), request.getProductId());

        Order order = new Order();
        order.setUserId(request.getUserId());
        order.setProductId(request.getProductId());
        order.setQuantity(request.getQuantity());
        order.setAmount(request.getAmount());
        order.setStatus(OrderStatus.CREATED);

        Order savedOrder = orderRepository.save(order);
        log.info("Order created successfully: {}", savedOrder.getOrderId());

        return convertToResponse(savedOrder);
    }

    @Transactional
    public OrderResponse getOrder(String orderId) {
        log.info("Getting order: {}", orderId);

        Optional<Order> order = orderRepository.findByOrderId(orderId);
        if (order.isEmpty()) {
            throw new RuntimeException("Order not found: " + orderId);
        }

        return convertToResponse(order.get());
    }

    @Transactional
    public void cancelOrder(String orderId) {
        log.info("Cancelling order: {}", orderId);

        Optional<Order> order = orderRepository.findByOrderId(orderId);
        if (order.isEmpty()) {
            throw new RuntimeException("Order not found: " + orderId);
        }

        Order orderEntity = order.get();
        orderEntity.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(orderEntity);

        log.info("Order cancelled successfully: {}", orderId);
    }

    @Transactional
    public void completeOrder(String orderId) {
        log.info("Completing order: {}", orderId);

        Optional<Order> order = orderRepository.findByOrderId(orderId);
        if (order.isEmpty()) {
            throw new RuntimeException("Order not found: " + orderId);
        }

        Order orderEntity = order.get();
        orderEntity.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(orderEntity);

        log.info("Order completed successfully: {}", orderId);
    }

    private OrderResponse convertToResponse(Order order) {
        return new OrderResponse(
            order.getOrderId(),
            order.getUserId(),
            order.getProductId(),
            order.getQuantity(),
            order.getAmount(),
            order.getStatus().name(),
            order.getCreatedAt()
        );
    }
}