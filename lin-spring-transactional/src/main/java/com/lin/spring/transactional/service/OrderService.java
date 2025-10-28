package com.lin.spring.transactional.service;

import com.lin.spring.transactional.dto.OrderCreatedEvent;
import com.lin.spring.transactional.entity.Order;
import com.lin.spring.transactional.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrderService {


    private final OrderRepository orderRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public Order createOrder(String customerName, Double amount) {
        System.out.println("开始创建订单...");

        // 保存订单到数据库
        Order order = new Order(customerName, amount);
        Order savedOrder = orderRepository.save(order);

        System.out.println("订单已保存到数据库，ID: " + savedOrder.getId());

        // 发布事件
        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getCustomerName(),
                savedOrder.getAmount()
        );
        eventPublisher.publishEvent(event);

        System.out.println("订单创建事件已发布");

        return savedOrder;
    }

    public Order createOrderNoTransactional(String customerName, Double amount) {
        System.out.println("开始创建订单...");

        // 保存订单到数据库
        Order order = new Order(customerName, amount);
        Order savedOrder = orderRepository.save(order);

        System.out.println("订单已保存到数据库，ID: " + savedOrder.getId());

        // 发布事件
        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getCustomerName(),
                savedOrder.getAmount()
        );
        eventPublisher.publishEvent(event);

        System.out.println("订单创建事件已发布");

        return savedOrder;
    }

    // 模拟事务失败的情况
    @Transactional
    public Order createOrderWithError(String customerName, Double amount) {
        System.out.println("开始创建订单（将会失败）...");

        Order order = new Order(customerName, amount);
        Order savedOrder = orderRepository.save(order);

        System.out.println("订单已保存到数据库，ID: " + savedOrder.getId());

        // 发布事件
        OrderCreatedEvent event = new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getCustomerName(),
                savedOrder.getAmount()
        );
        eventPublisher.publishEvent(event);

        System.out.println("订单创建事件已发布");

        // 抛出异常，导致事务回滚
        throw new RuntimeException("模拟业务异常，事务将回滚");
    }
}
