package com.lin.spring.orders.order.service;

import com.lin.spring.orders.common.event.EventTypes;
import com.lin.spring.orders.order.domain.Order;
import com.lin.spring.orders.order.dto.CancellationResponse;
import com.lin.spring.orders.order.dto.CreateOrderRequest;
import com.lin.spring.orders.order.dto.OrderResponse;
import com.lin.spring.orders.order.exception.IdempotencyConflictException;
import com.lin.spring.orders.order.exception.OrderNotFoundException;
import com.lin.spring.orders.order.outbox.OutboxRecorder;
import com.lin.spring.orders.order.pricing.PriceCalculator;
import com.lin.spring.orders.order.repository.OrderRepository;
import com.lin.spring.orders.order.statemachine.OrderStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PriceCalculator priceCalculator;
    private final OrderPersistence orderPersistence;
    private final OutboxRecorder outboxRecorder;

    /**
     * 创建订单（幂等）：
     * 1. 快速路径：按 (customerId, idempotencyKey) 查已有订单；
     * 2. 服务端定价（REST 调用在事务外，避免长事务占用连接）；
     * 3. 事务内：订单 + 明细 + ORDER_CREATED outbox 事件；
     * 4. 并发竞态由数据库唯一约束 uq_order_idempotency 兜底：冲突后回查返回既有订单。
     */
    public CreationResult createOrder(long customerId, String idempotencyKey, CreateOrderRequest request) {
        validateItems(request.items());
        String fingerprint = fingerprint(request.items());

        // 快速路径：同一请求重放（如客户端超时重试）
        var existing = orderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey);
        if (existing.isPresent()) {
            return toResultOf(existing.get(), fingerprint);
        }

        // 服务端定价并快照（事务外执行远程调用）
        PriceCalculator.PricedOrder priced = priceCalculator.priceAll(request.items());

        try {
            return orderPersistence.persistNewOrder(customerId, idempotencyKey, fingerprint, priced);
        } catch (DataIntegrityViolationException race) {
            // 并发相同幂等键：唯一约束裁决胜者，败者回查返回同一订单
            log.warn("Idempotency race on key={} for customer={}, returning existing order: {}",
                    idempotencyKey, customerId, race.getMessage());
            var loser = orderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey)
                    .orElseThrow(() -> new IllegalStateException(
                            "Unique constraint hit but order not found for key=" + idempotencyKey));
            return toResultOf(loser, fingerprint);
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, long customerId) {
        // 越权防护：查询他人订单一律 404，不泄露存在性
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getCustomerId() == customerId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));
        return OrderResponse.from(order);
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(long customerId, Pageable pageable) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(OrderResponse::summary);
    }

    /**
     * 取消订单（条件状态转换防竞态）：
     * 仅当当前状态在可取消集合内才置 CANCELLED；取消成功才发布 OrderCancelled 事件，
     * 由库存服务释放预留、支付服务对已完成的支付发起退款（补偿）。
     */
    @Transactional
    public CancellationResponse cancel(UUID orderId, long customerId) {
        Order order = orderRepository.findById(orderId)
                .filter(o -> o.getCustomerId() == customerId)
                .orElseThrow(() -> new OrderNotFoundException("Order not found: " + orderId));

        int updated = orderRepository.transitionIf(
                orderId, OrderStatus.cancellable(), OrderStatus.CANCELLED);
        if (updated == 1) {
            outboxRecorder.append(order, EventTypes.ORDER_CANCELLED,
                    Map.of("orderId", orderId.toString(),
                            "reason", "CUSTOMER_REQUEST"));
            log.info("Order {} cancelled by customer {}", orderId, customerId);
            return new CancellationResponse(orderId, OrderStatus.CANCELLED, true,
                    "Order cancelled; compensation (inventory release / refund) initiated");
        }
        OrderStatus current = orderRepository.findById(orderId).map(Order::getStatus).orElse(order.getStatus());
        log.info("Cancel rejected for order {} in state {}", orderId, current);
        return new CancellationResponse(orderId, current, false,
                "Order cannot be cancelled in state " + current);
    }

    private CreationResult toResultOf(Order existing, String fingerprint) {
        if (!existing.getRequestFingerprint().equals(fingerprint)) {
            // 同键不同负载：显式拒绝，绝不静默返回无关订单
            throw new IdempotencyConflictException(
                    "Idempotency key already used with a different request payload");
        }
        return new CreationResult(existing.getId(), existing.getStatus(), true);
    }

    private void validateItems(List<CreateOrderRequest.Item> items) {
        Set<Long> productIds = new HashSet<>();
        for (CreateOrderRequest.Item item : items) {
            if (!productIds.add(item.productId())) {
                throw new IllegalArgumentException("Duplicate productId in request: " + item.productId()
                        + " (merge quantities into a single line)");
            }
        }
    }

    /** 归一化请求指纹（SHA-256）：productId:quantity 按 productId 排序后拼接。 */
    static String fingerprint(List<CreateOrderRequest.Item> items) {
        String normalized = items.stream()
                .sorted(Comparator.comparing(CreateOrderRequest.Item::productId))
                .map(i -> i.productId() + ":" + i.quantity())
                .reduce("", (a, b) -> a + "|" + b);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(normalized.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record CreationResult(UUID orderId, OrderStatus status, boolean duplicate) {
    }
}
