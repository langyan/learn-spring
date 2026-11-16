package com.lin.spring.orders.order.statemachine;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** 状态机契约：合法转换放行、非法转换拒绝（过期/乱序事件防护）、终态封闭。 */
class OrderStatusTest {

    @Test
    void happyPathTransitionsAreLegal() {
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.PAYMENT_PENDING)).isTrue();
        assertThat(OrderStatus.PAYMENT_PENDING.canTransitionTo(OrderStatus.PAYMENT_UNKNOWN)).isTrue();
        assertThat(OrderStatus.PAYMENT_UNKNOWN.canTransitionTo(OrderStatus.CONFIRMED)).isTrue();
        assertThat(OrderStatus.CONFIRMED.canTransitionTo(OrderStatus.SHIPPED)).isTrue();
        assertThat(OrderStatus.SHIPPED.canTransitionTo(OrderStatus.DELIVERED)).isTrue();
    }

    @Test
    void illegalTransitionsAreRejected() {
        // 过期的 PAYMENT_COMPLETED 不能把已取消的订单推进到 CONFIRMED
        assertThat(OrderStatus.CANCELLED.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
        assertThat(OrderStatus.PENDING.canTransitionTo(OrderStatus.CONFIRMED)).isFalse();
        assertThat(OrderStatus.DELIVERED.canTransitionTo(OrderStatus.CANCELLED)).isFalse();
    }

    @Test
    void terminalStatesAreClosed() {
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(OrderStatus.DELIVERED.isTerminal()).isTrue();
        assertThat(OrderStatus.PAYMENT_FAILED.isTerminal()).isTrue();
        assertThat(OrderStatus.PENDING.isTerminal()).isFalse();
    }

    @Test
    void cancellableStatesCoverPrePaymentAndUnknown() {
        assertThat(OrderStatus.cancellable()).contains(
                OrderStatus.PENDING, OrderStatus.INVENTORY_RESERVED,
                OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_UNKNOWN, OrderStatus.CONFIRMED);
    }
}
