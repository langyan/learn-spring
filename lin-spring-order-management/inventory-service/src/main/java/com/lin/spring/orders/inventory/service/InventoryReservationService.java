package com.lin.spring.orders.inventory.service;

import com.lin.spring.orders.common.event.EventTypes;
import com.lin.spring.orders.inventory.domain.InventoryReservation;
import com.lin.spring.orders.inventory.outbox.OutboxRecorder;
import com.lin.spring.orders.inventory.repository.InventoryRepository;
import com.lin.spring.orders.inventory.repository.InventoryReservationRepository;
import com.lin.spring.orders.inventory.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 库存预留核心（文章第 9 节）：
 * 预留 = 原子条件 UPDATE + 受影响行数校验（防超卖）；多商品按 productId 升序处理（防死锁）；
 * 释放/确认 = 预留行状态条件转移 + 库存回补，天然幂等（重复调用不得重复回补库存）。
 *
 * <p>幂等登记（tryRegister）与业务更新在同一个 @Transactional 方法内——要么都生效，要么都回滚，
 * 杜绝"登记已提交但业务未执行，重放被跳过"的丢事件窗口。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryReservationService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final OutboxRecorder outboxRecorder;

    @Value("${app.reservation.ttl-minutes}")
    private long reservationTtlMinutes;

    public record ItemQuantity(long productId, int quantity) {
    }

    /**
     * 为订单预留库存（一个本地事务：幂等登记 + 原子扣减 + 预留行 + Outbox 事件）。
     * 任何一件商品库存不足 → 抛出异常 → 整个事务回滚（不留部分预留），由调用方转写 INVENTORY_REJECTED。
     */
    @Transactional
    public void reserveForOrder(UUID eventId, UUID orderId, Long customerId, List<ItemQuantity> items) {
        if (processedEventRepository.tryRegister(eventId, EventTypes.ORDER_CREATED) == 0) {
            log.debug("Duplicate ORDER_CREATED {} for order {} skipped", eventId, orderId);
            return;
        }
        List<InventoryReservation> existing = reservationRepository.findByOrderId(orderId);
        if (!existing.isEmpty()) {
            log.warn("Order {} already has reservations ({}), replay ignored", orderId, existing.size());
            return;
        }

        Instant expiresAt = Instant.now().plusSeconds(reservationTtlMinutes * 60);
        // 固定锁排序：所有实例以相同顺序访问商品行，避免交叉死锁
        List<ItemQuantity> sorted = items.stream()
                .sorted(Comparator.comparingLong(ItemQuantity::productId))
                .toList();

        List<Map<String, Object>> reservedItems = new ArrayList<>();
        for (ItemQuantity item : sorted) {
            int updated = inventoryRepository.tryReserve(item.productId(), item.quantity());
            if (updated != 1) {
                // 恰好一行应被更新；0 行 = 库存不足，事务回滚
                throw new InsufficientStockException(item.productId(),
                        "Insufficient stock for product " + item.productId());
            }
            reservationRepository.save(InventoryReservation.builder()
                    .id(UUID.randomUUID())
                    .orderId(orderId)
                    .productId(item.productId())
                    .quantity(item.quantity())
                    .status(InventoryReservation.Status.RESERVED)
                    .expiresAt(expiresAt)
                    .build());
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("productId", item.productId());
            line.put("quantity", item.quantity());
            reservedItems.add(line);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("items", reservedItems);
        payload.put("expiresAt", expiresAt.toString());
        outboxRecorder.append(orderId, customerId, EventTypes.INVENTORY_RESERVED, payload);
        log.info("Inventory reserved for order {} ({} line(s))", orderId, sorted.size());
    }

    /** 事件触发（有 eventId 幂等保障）的释放。 */
    @Transactional
    public void releaseForOrderDedup(UUID eventId, String eventType,
                                     UUID orderId, Long customerId, String reason) {
        if (processedEventRepository.tryRegister(eventId, eventType) == 0) {
            return;
        }
        releaseCore(orderId, customerId, reason);
    }

    /** 系统内部触发（预留过期回收，无重放来源）的释放。 */
    @Transactional
    public void releaseForOrder(UUID orderId, Long customerId, String reason) {
        releaseCore(orderId, customerId, reason);
    }

    private void releaseCore(UUID orderId, Long customerId, String reason) {
        List<InventoryReservation> reserved =
                reservationRepository.findByOrderIdAndStatus(orderId, InventoryReservation.Status.RESERVED);
        int released = 0;
        for (InventoryReservation r : reserved) {
            if (reservationRepository.releaseIfReserved(r.getId()) == 1) {
                inventoryRepository.releaseReserved(r.getProductId(), r.getQuantity());
                released++;
            }
        }
        if (released > 0) {
            outboxRecorder.append(orderId, customerId, EventTypes.INVENTORY_RELEASED,
                    Map.of("orderId", orderId.toString(), "reason", reason, "releasedLines", released));
            log.warn("Released {} reservation line(s) for order {} (reason={})", released, orderId, reason);
        } else {
            log.info("Nothing to release for order {} (reason={})", orderId, reason);
        }
    }

    /** 支付成功、订单 CONFIRMED 后确认预留（幂等登记 + 结转在同意图事务）。 */
    @Transactional
    public void confirmForOrder(UUID eventId, UUID orderId, Long customerId) {
        if (processedEventRepository.tryRegister(eventId, EventTypes.ORDER_CONFIRMED) == 0) {
            return;
        }
        List<InventoryReservation> reserved =
                reservationRepository.findByOrderIdAndStatus(orderId, InventoryReservation.Status.RESERVED);
        int confirmed = 0;
        for (InventoryReservation r : reserved) {
            if (reservationRepository.confirmIfReserved(r.getId()) == 1) {
                inventoryRepository.confirmReserved(r.getProductId(), r.getQuantity());
                confirmed++;
            }
        }
        if (confirmed == 0) {
            // 预留在确认前已过期释放：真实系统应告警并进入人工对账（README 讨论）
            log.error("CONFIRM for order {} found no active reservations — alert and reconcile manually", orderId);
        } else {
            log.info("Confirmed {} reservation line(s) for order {}", confirmed, orderId);
        }
    }
}
