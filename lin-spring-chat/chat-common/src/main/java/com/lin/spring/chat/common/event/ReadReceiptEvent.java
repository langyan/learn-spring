package com.lin.spring.chat.common.event;

/**
 * Kafka topic: read-receipts（key = conversationId）。
 * 已读位置上报事件：不存 per-message 已读，仅存每用户每会话最后已读消息位置（O(成员数) 存储）。
 */
public record ReadReceiptEvent(
        String conversationId,
        String userId,             // 谁读了
        String lastReadMessageId,
        long lastReadMessageAtMillis,
        long timestampMillis) {
}
