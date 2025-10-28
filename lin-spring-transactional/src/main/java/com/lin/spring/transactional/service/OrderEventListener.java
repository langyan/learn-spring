package com.lin.spring.transactional.service;

import com.lin.spring.transactional.dto.OrderCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class OrderEventListener {

    // AFTER_COMMIT: 只有在事务成功提交后才会执行
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreatedAfterCommit(OrderCreatedEvent event) {
        System.out.println("=== AFTER_COMMIT 监听器触发 ===");
        System.out.println("订单创建成功，开始后续处理:");
        System.out.println("- 订单ID: " + event.getOrderId());
        System.out.println("- 客户名称: " + event.getCustomerName());
        System.out.println("- 订单金额: " + event.getAmount());

        // 这里可以执行一些需要确保事务已提交的操作
        // 比如发送邮件、更新缓存、调用外部系统等
        System.out.println("- 发送订单确认邮件");
        System.out.println("- 更新用户积分");
        System.out.println("- 通知库存系统");
        System.out.println("=== AFTER_COMMIT 处理完成 ===\n");
    }

    // 对比：普通的事件监听器（同步执行，在事务内）
    @EventListener
    public void handleOrderCreatedSync(OrderCreatedEvent event) {
        System.out.println("=== 同步监听器触发（事务内） ===");
        System.out.println("订单ID: " + event.getOrderId() + " - 同步处理");
        System.out.println("=== 同步处理完成 ===\n");
    }

    // BEFORE_COMMIT: 在事务提交前执行
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handleOrderCreatedBeforeCommit(OrderCreatedEvent event) {
        System.out.println("=== BEFORE_COMMIT 监听器触发 ===");
        System.out.println("订单ID: " + event.getOrderId() + " - 事务提交前处理");
        System.out.println("=== BEFORE_COMMIT 处理完成 ===\n");
    }

    // AFTER_ROLLBACK: 只有在事务回滚后才会执行
    @TransactionalEventListener(phase = TransactionPhase.AFTER_ROLLBACK)
    public void handleOrderCreatedAfterRollback(OrderCreatedEvent event) {
        System.out.println("=== AFTER_ROLLBACK 监听器触发 ===");
        System.out.println("订单创建失败，执行回滚后处理:");
        System.out.println("- 订单ID: " + event.getOrderId());
        System.out.println("- 记录失败日志");
        System.out.println("- 通知运维人员");
        System.out.println("=== AFTER_ROLLBACK 处理完成 ===\n");
    }
}
