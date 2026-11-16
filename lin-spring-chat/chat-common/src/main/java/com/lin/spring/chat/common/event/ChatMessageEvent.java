package com.lin.spring.chat.common.event;

/**
 * Kafka topic: chat-messages（key = conversationId，同会话固定分区保证会话内有序）。
 * 聊天消息事件（含 TEXT 用户消息与 SYSTEM 群系统消息），由 chat-service 生产、扇出消费者消费。
 */
public record ChatMessageEvent(
        String id,
        String conversationId,
        String conversationType,   // DIRECT / GROUP
        String senderId,           // SYSTEM 消息为发起操作的 actor
        String senderNickname,
        String type,               // TEXT / SYSTEM
        String content,
        long createdAtMillis,
        boolean recalled) {
}
