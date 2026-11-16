package com.lin.spring.chat.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 未读数：chat:unread:{userId} Hash: conversationId -> count。
 * 扇出时 +1（发送者除外）；进入会话/上报已读时清零。列表接口免扫消息表。
 */
@Component
@RequiredArgsConstructor
public class UnreadStore {

    private static final String UNREAD_PREFIX = "chat:unread:";

    private final StringRedisTemplate redis;

    public void increment(String userId, String conversationId) {
        redis.opsForHash().increment(UNREAD_PREFIX + userId, conversationId, 1);
    }

    public void clear(String userId, String conversationId) {
        redis.opsForHash().delete(UNREAD_PREFIX + userId, conversationId);
    }

    public Map<String, Integer> all(String userId) {
        Map<Object, Object> raw = redis.opsForHash().entries(UNREAD_PREFIX + userId);
        Map<String, Integer> result = new HashMap<>();
        raw.forEach((k, v) -> result.put(k.toString(), Integer.parseInt(v.toString())));
        return result;
    }
}
