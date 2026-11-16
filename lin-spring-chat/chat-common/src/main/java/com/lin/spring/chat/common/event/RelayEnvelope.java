package com.lin.spring.chat.common.event;

/**
 * Kafka topic: gateway-{serverId}（key = userId）。
 * 扇出消费者 -> 目标网关实例的最终投递信封。网关为哑投递：不解析 payload 语义，
 * 直接 convertAndSendToUser(userId, destination, payload)。
 */
public record RelayEnvelope(
        String userId,
        String destination,   // 如 /queue/messages、/queue/receipts、/queue/presence、/queue/conversations
        Object payload) {     // 任意 JSON 对象（MessageDto / ReadReceiptEvent / ...）
}
