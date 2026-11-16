package com.lin.spring.orders.inventory.consumer;

import com.lin.spring.orders.common.event.EventTypes;
import com.lin.spring.orders.common.event.IntegrationEvent;
import com.lin.spring.orders.inventory.outbox.OutboxRecorder;
import com.lin.spring.orders.inventory.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * 库存不足时写 INVENTORY_REJECTED（独立事务：预留事务已回滚，不能在 rollback-only 事务里继续写）。
 */
@Component
@RequiredArgsConstructor
public class RejectionWriter {

    private final ProcessedEventRepository processedEventRepository;
    private final OutboxRecorder outboxRecorder;

    @Transactional
    public void writeRejection(IntegrationEvent event, UUID orderId, long failedProductId) {
        // 预留事务已回滚（其登记一并撤销），这里重新登记——两个动作在本事务内原子提交
        if (processedEventRepository.tryRegister(event.eventId(), EventTypes.ORDER_CREATED) == 0) {
            return;
        }
        outboxRecorder.append(orderId, event.customerId(), EventTypes.INVENTORY_REJECTED,
                Map.of("orderId", orderId.toString(),
                        "reason", "INSUFFICIENT_STOCK",
                        "failedProductId", failedProductId));
    }
}
