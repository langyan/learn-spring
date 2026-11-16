package com.lin.spring.orders.inventory;

import com.lin.spring.orders.inventory.domain.InventoryItem;
import com.lin.spring.orders.inventory.repository.InventoryRepository;
import com.lin.spring.orders.inventory.service.InsufficientStockException;
import com.lin.spring.orders.inventory.service.InventoryReservationService;
import com.lin.spring.orders.inventory.service.InventoryReservationService.ItemQuantity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 防超卖并发验证（文章第 9 节）：
 * 商品 410 仅剩 1 件，20 个订单同时预留——必须恰好 1 个成功、19 个库存不足，库存永不为负。
 * 使用 Testcontainers 真实 PostgreSQL：原子条件 UPDATE / CHECK 约束 / FOR UPDATE 行为与生产一致。
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        "spring.cache.type=none",
        "app.outbox.poll-fixed-delay-ms=3600000",
        "app.reservation.expiry-scan-fixed-delay-ms=3600000"
})
@Testcontainers
class InventoryOversellConcurrencyTest {

    private static final long LAST_UNIT_PRODUCT = 410L;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private InventoryReservationService reservationService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void exactlyOneWinnerWhenTwentyOrdersCompeteForLastUnit() throws Exception {
        resetStock(LAST_UNIT_PRODUCT, 1);

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger reserved = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        List<Future<?>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < threads; i++) {
            UUID orderId = UUID.randomUUID();
            UUID eventId = UUID.randomUUID();
            futures.add(executor.submit(() -> {
                start.await();
                try {
                    reservationService.reserveForOrder(eventId, orderId, 1L,
                            List.of(new ItemQuantity(LAST_UNIT_PRODUCT, 1)));
                    reserved.incrementAndGet();
                } catch (InsufficientStockException e) {
                    rejected.incrementAndGet();
                }
                return null;
            }));
        }
        start.countDown();
        for (Future<?> f : futures) {
            f.get();
        }
        executor.shutdown();

        assertThat(reserved.get()).isEqualTo(1);
        assertThat(rejected.get()).isEqualTo(threads - 1);

        InventoryItem item = inventoryRepository.findById(LAST_UNIT_PRODUCT).orElseThrow();
        assertThat(item.getAvailableQuantity()).isZero();
        assertThat(item.getReservedQuantity()).isEqualTo(1);
    }

    @Test
    void releaseTwiceRestoresStockOnlyOnce() {
        resetStock(101L, 100);

        UUID orderId = UUID.randomUUID();
        reservationService.reserveForOrder(UUID.randomUUID(), orderId, 7L,
                List.of(new ItemQuantity(101L, 3)));

        reservationService.releaseForOrder(orderId, 7L, "CUSTOMER_CANCEL");
        // 重复释放（事件重放/并发补偿路径）：不得重复回补库存
        reservationService.releaseForOrder(orderId, 7L, "CUSTOMER_CANCEL");

        InventoryItem item = inventoryRepository.findById(101L).orElseThrow();
        assertThat(item.getAvailableQuantity()).isEqualTo(100);
        assertThat(item.getReservedQuantity()).isZero();
    }

    @Test
    void multiItemReservationRollsBackAtomicallyWhenAnyLineInsufficient() {
        resetStock(101L, 100);
        resetStock(205L, 1);
        resetStock(301L, 200);

        UUID orderId = UUID.randomUUID();
        try {
            reservationService.reserveForOrder(UUID.randomUUID(), orderId, 9L, List.of(
                    new ItemQuantity(101L, 1),   // 可满足
                    new ItemQuantity(205L, 2),   // 不足（仅 1 件）→ 整单拒绝
                    new ItemQuantity(301L, 1)));
        } catch (InsufficientStockException expected) {
            // 部分预留必须整体回滚
        }

        assertThat(inventoryRepository.findById(101L).orElseThrow().getAvailableQuantity()).isEqualTo(100);
        assertThat(inventoryRepository.findById(205L).orElseThrow().getAvailableQuantity()).isEqualTo(1);
        assertThat(inventoryRepository.findById(301L).orElseThrow().getAvailableQuantity()).isEqualTo(200);
    }

    private void resetStock(long productId, int available) {
        InventoryItem item = inventoryRepository.findById(productId).orElseThrow();
        item.setAvailableQuantity(available);
        item.setReservedQuantity(0);
        inventoryRepository.saveAndFlush(item);
    }
}
