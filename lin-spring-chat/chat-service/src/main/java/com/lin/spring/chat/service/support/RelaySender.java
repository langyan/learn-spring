package com.lin.spring.chat.service.support;

import com.lin.spring.chat.common.event.RelayEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 跨实例最终投递：把面向某用户的消息发到其所在网关实例的专属 topic（gateway-{serverId}，
 * key=userId）。相比 Redis Pub/Sub，Kafka 提供持久化与重放（网关重启可补消费），为生产推荐方案。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelaySender {

    public static final String GATEWAY_TOPIC_PREFIX = "gateway-";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final SessionRegistryReader sessionRegistry;

    /** 向用户所有在线设备所在网关投递（多设备多网关同收） */
    public void relayToUser(String userId, String destination, Object payload) {
        var servers = sessionRegistry.connectedServers(userId);
        if (servers.isEmpty()) {
            return; // 离线，由调用方走 undelivered 兜底
        }
        RelayEnvelope envelope = new RelayEnvelope(userId, destination, payload);
        for (String serverId : servers) {
            kafkaTemplate.send(GATEWAY_TOPIC_PREFIX + serverId, userId, envelope);
        }
    }

    /** 用户是否完全离线（没有任何网关会话）——扇出消费者用于决定是否落 undelivered */
    public boolean hasNoActiveSession(String userId) {
        return sessionRegistry.connectedServers(userId).isEmpty();
    }
}
