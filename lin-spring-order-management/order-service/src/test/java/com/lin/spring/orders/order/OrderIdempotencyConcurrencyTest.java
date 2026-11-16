package com.lin.spring.orders.order;

import com.lin.spring.orders.order.dto.CreateOrderRequest;
import com.lin.spring.orders.order.exception.IdempotencyConflictException;
import com.lin.spring.orders.order.pricing.PriceCalculator;
import com.lin.spring.orders.order.pricing.PriceCalculator.PricedLine;
import com.lin.spring.orders.order.pricing.PriceCalculator.PricedOrder;
import com.lin.spring.orders.order.pricing.PriceCalculator.PricedQuantity;
import com.lin.spring.orders.order.repository.OrderRepository;
import com.lin.spring.orders.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 下单幂等并发验证（文章第 6/8 节）：
 * 相同 (customerId, Idempotency-Key) 的并发请求必须只创建一个订单；
 * 同键不同负载必须显式拒绝。唯一约束是最终裁决者，而非"先查后插"。
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "app.outbox.poll-fixed-delay-ms=3600000"
})
@Testcontainers
class OrderIdempotencyConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @MockBean
    private PriceCalculator priceCalculator;

    @Test
    void concurrentDuplicateKeyCreatesExactlyOneOrder() throws Exception {
        when(priceCalculator.priceAll(any())).thenReturn(new PricedOrder(List.of(
                new PricedQuantity(new PricedLine(101L, "Mechanical Keyboard",
                        new BigDecimal("79.90"), "USD"), 2)), "USD"));

        long customerId = 42L;
        String idempotencyKey = "concurrent-test-" + UUID.randomUUID();
        var request = new CreateOrderRequest(List.of(new CreateOrderRequest.Item(101L, 2)));

        int threads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<UUID> firstOrderId = new AtomicReference<>();
        List<Future<UUID>> futures = IntStream.range(0, threads)
                .mapToObj(i -> executor.submit(() -> {
                    start.await();
                    var result = orderService.createOrder(customerId, idempotencyKey, request);
                    firstOrderId.compareAndSet(null, result.orderId());
                    return result.orderId();
                }))
                .toList();
        start.countDown();
        for (Future<UUID> f : futures) {
            f.get();
        }
        executor.shutdown();

        // 所有请求拿到同一个订单 ID
        for (Future<UUID> f : futures) {
            assertThat(f.get()).isEqualTo(firstOrderId.get());
        }
        // 数据库中该 (customerId, key) 只有一行
        assertThat(orderRepository.findByCustomerIdAndIdempotencyKey(customerId, idempotencyKey))
                .hasValueSatisfying(o -> assertThat(o.getId()).isEqualTo(firstOrderId.get()));
    }

    @Test
    void sameKeyDifferentPayloadIsRejected() {
        when(priceCalculator.priceAll(any())).thenReturn(new PricedOrder(List.of(
                new PricedQuantity(new PricedLine(101L, "Mechanical Keyboard",
                        new BigDecimal("79.90"), "USD"), 1)), "USD"));

        long customerId = 43L;
        String key = "conflict-test-" + UUID.randomUUID();

        orderService.createOrder(customerId, key,
                new CreateOrderRequest(List.of(new CreateOrderRequest.Item(101L, 1))));

        // 同键不同数量（不同负载）→ 409 冲突，绝不静默返回无关订单
        assertThrows(IdempotencyConflictException.class, () ->
                orderService.createOrder(customerId, key,
                        new CreateOrderRequest(List.of(new CreateOrderRequest.Item(101L, 5)))));
    }

    @Test
    void sequentialReplayReturnsExistingOrder() {
        when(priceCalculator.priceAll(any())).thenReturn(new PricedOrder(List.of(
                new PricedQuantity(new PricedLine(205L, "4K Monitor",
                        new BigDecimal("349.00"), "USD"), 1)), "USD"));

        long customerId = 44L;
        String key = "replay-test-" + UUID.randomUUID();
        var request = new CreateOrderRequest(List.of(new CreateOrderRequest.Item(205L, 1)));

        var first = orderService.createOrder(customerId, key, request);
        var replay = orderService.createOrder(customerId, key, request);

        assertThat(replay.orderId()).isEqualTo(first.orderId());
        assertThat(replay.duplicate()).isTrue();
    }
}
