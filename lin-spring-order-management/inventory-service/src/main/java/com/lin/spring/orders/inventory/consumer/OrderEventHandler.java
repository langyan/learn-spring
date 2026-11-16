package com.lin.spring.orders.inventory.consumer;

import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.inventory.service.InsufficientStockException;
import com.lin.spring.orders.inventory.service.InventoryReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 库存服务的事件处理（Saga 参与方，去重已内聚在各 @Transactional 服务方法中）：
 * ORDER_CREATED → 预留（不足则转写 INVENTORY_REJECTED）
 * ORDER_CANCELLED → 释放
 * ORDER_CONFIRMED → 确认
 * PAYMENT_FAILED → 释放（文章：付款永久失败必须释放库存预留）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventHandler {

    private final InventoryReservationService reservationService;
    private final RejectionWriter rejectionWriter;

    public void onOrderCreated(IntegrationEvent event) {
        UUID orderId = UUID.fromString((String) event.payloadValue("orderId"));
        List<InventoryReservationService.ItemQuantity> items = parseItems(event);
        try {
            reservationService.reserveForOrder(event.eventId(), orderId, event.customerId(), items);
        } catch (InsufficientStockException e) {
            log.warn("Order {} rejected: {}", orderId, e.getMessage());
            rejectionWriter.writeRejection(event, orderId, e.productId());
        }
    }

    public void onOrderCancelled(IntegrationEvent event) {
        UUID orderId = UUID.fromString((String) event.payloadValue("orderId"));
        reservationService.releaseForOrderDedup(event.eventId(), "ORDER_CANCELLED",
                orderId, event.customerId(), "CUSTOMER_CANCEL");
    }

    public void onOrderConfirmed(IntegrationEvent event) {
        UUID orderId = UUID.fromString((String) event.payloadValue("orderId"));
        reservationService.confirmForOrder(event.eventId(), orderId, event.customerId());
    }

    public void onPaymentFailed(IntegrationEvent event) {
        UUID orderId = UUID.fromString((String) event.payloadValue("orderId"));
        reservationService.releaseForOrderDedup(event.eventId(), "PAYMENT_FAILED",
                orderId, event.customerId(), "PAYMENT_FAILED");
    }

    @SuppressWarnings("unchecked")
    private List<InventoryReservationService.ItemQuantity> parseItems(IntegrationEvent event) {
        Object raw = event.payload().get("items");
        if (!(raw instanceof List<?> list)) {
            throw new IllegalArgumentException("ORDER_CREATED payload missing items");
        }
        return ((List<Map<String, Object>>) list).stream()
                .map(m -> new InventoryReservationService.ItemQuantity(
                        ((Number) m.get("productId")).longValue(),
                        ((Number) m.get("quantity")).intValue()))
                .toList();
    }
}
