package com.lin.spring.chat.service.support;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 读取用户 -> 网关实例映射（写入方为 chat-gateway 的 SessionRegistry，共享同一 key 契约）：
 * chat:session:{userId} Hash: sessionId -> serverId。一个用户多设备可能挂在多个网关。
 */
@Component
@RequiredArgsConstructor
public class SessionRegistryReader {

    private static final String SESSION_PREFIX = "chat:session:";

    private final StringRedisTemplate redis;

    public Set<String> connectedServers(String userId) {
        Map<Object, Object> sessions = redis.opsForHash().entries(SESSION_PREFIX + userId);
        return sessions.values().stream().map(Object::toString).collect(Collectors.toSet());
    }
}
