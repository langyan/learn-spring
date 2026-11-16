package com.lin.spring.chat.gateway.relay;

import com.lin.spring.chat.common.event.RelayEnvelope;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 哑投递消费者（对齐参考文章 GatewayRelay）：消费本实例专属 topic gateway-{serverId}，
 * 把载荷直接送达挂在本地连接上的用户 WebSocket 队列。网关不解析业务语义，天然无状态。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayRelayConsumer {

    private final SimpMessagingTemplate messagingTemplate;

    @KafkaListener(topics = "#{@gatewayTopicNames.relayTopic}", groupId = "#{@gatewayTopicNames.groupId}")
    public void onRelay(RelayEnvelope envelope) {
        try {
            messagingTemplate.convertAndSendToUser(
                    envelope.userId(), envelope.destination(), envelope.payload());
        } catch (Exception e) {
            // 用户刚断开时 user queue 无会话，Spring 静默忽略即可；记录便于定位
            log.warn("投递失败: user={} destination={}", envelope.userId(), envelope.destination());
        }
    }
}
