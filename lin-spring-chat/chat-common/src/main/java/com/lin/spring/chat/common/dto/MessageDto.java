package com.lin.spring.chat.common.dto;

/**
 * 投递到客户端的消息载荷：既作为 REST 历史接口返回体，也作为 RelayEnvelope
 * payload 经 /user/queue/messages 推送。时间为 epoch 毫秒（UTC），客户端本地化展示。
 */
public record MessageDto(
        String id,
        String conversationId,
        String conversationType,   // DIRECT / GROUP
        String senderId,
        String senderNickname,
        String type,               // TEXT / SYSTEM
        String content,
        long createdAtMillis,
        boolean recalled) {
}
