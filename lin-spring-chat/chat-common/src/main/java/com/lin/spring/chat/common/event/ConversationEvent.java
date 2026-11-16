package com.lin.spring.chat.common.event;

/**
 * Kafka topic: conversation-events（key = conversationId）。
 * 会话生命周期事件：建群/邀请/移除/退群/改名/解散/消息撤回，驱动各成员客户端刷新会话列表与群信息。
 */
public record ConversationEvent(
        String conversationId,
        String action,        // CREATED / MEMBER_ADDED / MEMBER_REMOVED / MEMBER_LEFT / RENAMED / DISSOLVED / MESSAGE_RECALLED
        String actorId,
        String refId,         // 附加引用：撤回=messageId、邀请/移除=目标用户 id（可空）
        long timestampMillis) {

    public static final String CREATED = "CREATED";
    public static final String MEMBER_ADDED = "MEMBER_ADDED";
    public static final String MEMBER_REMOVED = "MEMBER_REMOVED";
    public static final String MEMBER_LEFT = "MEMBER_LEFT";
    public static final String RENAMED = "RENAMED";
    public static final String DISSOLVED = "DISSOLVED";
    public static final String MESSAGE_RECALLED = "MESSAGE_RECALLED";
}
