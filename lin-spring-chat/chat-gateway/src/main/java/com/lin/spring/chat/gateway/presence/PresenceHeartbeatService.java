package com.lin.spring.chat.gateway.presence;

import com.lin.spring.chat.common.event.PresenceEvent;
import com.lin.spring.chat.gateway.session.SessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 在线状态心跳（对齐参考文章 PresenceService）：
 * - 心跳：ZSet chat:presence score=毫秒时间戳；首次上线（此前无有效 score）广播 ONLINE
 * - 断连：移除会话后若无剩余会话则判离线并广播 OFFLINE
 * - 崩溃场景由 chat-service 定时淘汰任务兜底（不依赖 DISCONNECT 事件）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PresenceHeartbeatService {

    public static final String PRESENCE_KEY = "chat:presence";
    private static final String TOPIC_PRESENCE = "presence-events";

    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SessionRegistry sessionRegistry;

    @Value("${app.presence.timeout-seconds:30}")
    private long timeoutSeconds;

    /** 客户端 /app/heartbeat 每 20s 上报一次 */
    public void heartbeat(String userId) {
        boolean wasOffline = !isOnline(userId);
        redis.opsForZSet().add(PRESENCE_KEY, userId, System.currentTimeMillis());
        if (wasOffline) {
            kafkaTemplate.send(TOPIC_PRESENCE, userId,
                    new PresenceEvent(userId, true, System.currentTimeMillis()));
            log.info("用户上线: {}", userId);
        }
    }

    /** WebSocket 断连：仅当该用户无剩余会话（多设备全部下线）才判离线 */
    public void onDisconnect(String userId) {
        if (sessionRegistry.sessionCount(userId) > 0) {
            return; // 其他设备仍在线
        }
        redis.opsForZSet().remove(PRESENCE_KEY, userId);
        kafkaTemplate.send(TOPIC_PRESENCE, userId,
                new PresenceEvent(userId, false, System.currentTimeMillis()));
        log.info("用户离线: {}", userId);
    }

    private boolean isOnline(String userId) {
        Double score = redis.opsForZSet().score(PRESENCE_KEY, userId);
        return score != null && (System.currentTimeMillis() - score) < timeoutSeconds * 1000;
    }
}
