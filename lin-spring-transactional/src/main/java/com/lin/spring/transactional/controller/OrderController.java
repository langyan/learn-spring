package com.lin.spring.transactional.controller;

import com.lin.spring.transactional.dto.CreateOrderRequest;
import com.lin.spring.transactional.entity.Order;
import com.lin.spring.transactional.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {


    private final OrderService orderService;

    // 测试成功场景
    @PostMapping("/create")
    public ResponseEntity<Order> createOrder(@RequestBody CreateOrderRequest request) {
        try {
            Order order = orderService.createOrder(request.getCustomerName(), request.getAmount());
            return ResponseEntity.ok(order);
        } catch (Exception e) {
            return ResponseEntity.status(500).build();
        }
    }

    // 测试失败场景（事务回滚）
    @PostMapping("/create-with-error")
    public ResponseEntity<String> createOrderWithError(@RequestBody CreateOrderRequest request) {
        try {
            orderService.createOrderWithError(request.getCustomerName(), request.getAmount());
            return ResponseEntity.ok("不会执行到这里");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("订单创建失败: " + e.getMessage());
        }
    }

    @PostMapping("/create-no-transactional")
    public ResponseEntity<String> createOrderNoTransactional(@RequestBody CreateOrderRequest request) {
        try {
            orderService.createOrderNoTransactional(request.getCustomerName(), request.getAmount());
            return ResponseEntity.ok("不会执行到这里");
        } catch (Exception e) {
            return ResponseEntity.status(500).body("订单创建失败: " + e.getMessage());
        }
    }
}

