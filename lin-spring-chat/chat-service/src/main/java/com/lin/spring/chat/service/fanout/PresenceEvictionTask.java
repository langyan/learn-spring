package com.lin.spring.chat.service.fanout;

import com.lin.spring.chat.common.event.PresenceEvent;
import com.lin.spring.chat.service.support.PresenceStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 在线状态淘汰任务：处理网关崩溃/断网等不优雅断连（DISCONNECT 事件不可靠）。
 * 最后心跳超过 30s 的用户判离线：移出 ZSet 并广播 OFFLINE 事件。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceEvictionTask {

    private static final int BATCH_LIMIT = 200;

    private final PresenceStore presenceStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.presence.evict-fixed-rate-ms:10000}")
    private long evictFixedRateMs;

    @Scheduled(fixedRateString = "${app.presence.evict-fixed-rate-ms:10000}")
    public void evictStalePresence() {
        var stale = presenceStore.evictStale(BATCH_LIMIT);
        for (String userId : stale) {
            presenceStore.remove(userId);
            kafkaTemplate.send("presence-events", userId,
                    new PresenceEvent(userId, false, System.currentTimeMillis()));
            log.info("心跳超时判离线: {}", userId);
        }
    }
}
