package com.lin.spring.chat.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis ZSet 在线状态存储：score = 最后心跳毫秒。
 * 心跳写入由 chat-gateway 完成（本服务只读 + 定时淘汰），两边共享同一 key 契约。
 */
@Component
@RequiredArgsConstructor
public class PresenceStore {

    public static final String PRESENCE_KEY = "chat:presence";

    private final StringRedisTemplate redis;

    @Value("${app.presence.timeout-seconds:30}")
    private long timeoutSeconds;

    public boolean isOnline(String userId) {
        Double score = redis.opsForZSet().score(PRESENCE_KEY, userId);
        return score != null && (System.currentTimeMillis() - score) < timeoutSeconds * 1000;
    }

    public Set<String> filterOnline(Collection<String> userIds) {
        return userIds.stream().filter(this::isOnline).collect(Collectors.toSet());
    }

    /** 批量查询在线状态（避免逐个 score 请求） */
    public Map<String, Boolean> onlineMap(Collection<String> userIds) {
        return userIds.stream().collect(Collectors.toMap(id -> id, this::isOnline));
    }

    /** 淘汰过期用户：返回最后心跳早于 cutoff 的用户（分批 limit，避免大 key 阻塞） */
    public List<String> evictStale(int limit) {
        long cutoff = System.currentTimeMillis() - timeoutSeconds * 1000;
        Set<String> stale = redis.opsForZSet().rangeByScore(PRESENCE_KEY, 0, cutoff, 0, limit);
        return List.copyOf(stale == null ? Set.of() : stale);
    }

    public void remove(String userId) {
        redis.opsForZSet().remove(PRESENCE_KEY, userId);
    }
}
