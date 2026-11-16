package com.lin.spring.chat.common.event;

/**
 * Kafka topic: presence-events（key = userId）。上线/下线广播：网关心跳首跳上线、
 * 断连（无剩余会话）或 chat-service 定时淘汰任务判离线时产生。
 */
public record PresenceEvent(
        String userId,
        boolean online,
        long timestampMillis) {
}
