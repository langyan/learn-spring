package com.lin.spring.chat.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 已读位置：chat:read:{conversationId}:{userId} = 最后已读消息 createdAt 毫秒。
 * 只存位置不存 per-message 已读：存储从 O(消息x成员) 降为 O(成员)；判定"已读"即位置 >= 消息时间。
 */
@Component
@RequiredArgsConstructor
public class ReadPositionStore {

    private static final String READ_KEY = "chat:read:%s:%s"; // conversationId:userId

    private final StringRedisTemplate redis;

    public void set(String conversationId, String userId, long lastReadMessageAtMillis) {
        redis.opsForValue().set(key(conversationId, userId), String.valueOf(lastReadMessageAtMillis));
    }

    /** 批量取成员已读位置（multiGet），返回 userId -> 位置毫秒（未读过不在 map 中） */
    public Map<String, Long> positions(String conversationId, List<String> memberIds) {
        List<String> keys = memberIds.stream().map(id -> key(conversationId, id)).toList();
        List<String> values = redis.opsForValue().multiGet(keys);
        Map<String, Long> result = new HashMap<>();
        for (int i = 0; i < memberIds.size(); i++) {
            String v = values == null ? null : values.get(i);
            if (v != null) {
                result.put(memberIds.get(i), Long.parseLong(v));
            }
        }
        return result;
    }

    private String key(String conversationId, String userId) {
        return String.format(READ_KEY, conversationId, userId);
    }
}
