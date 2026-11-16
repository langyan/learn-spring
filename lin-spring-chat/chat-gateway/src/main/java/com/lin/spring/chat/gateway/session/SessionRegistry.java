package com.lin.spring.chat.gateway.session;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * 用户 -> 网关实例映射（对齐参考文章 SessionRegistry）：
 * - chat:session:{userId} Hash: sessionId -> serverId（单用户多设备多网关）
 * - chat:server:{serverId} Set: 该实例连接的用户（优雅关闭时批量清理）
 * - 24h TTL 兜底清理僵尸会话
 * chat-service 的扇出消费者读取同一 key 契约定位目标网关。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRegistry {

    private static final String SESSION_PREFIX = "chat:session:";
    private static final String SERVER_PREFIX = "chat:server:";

    private final StringRedisTemplate redis;

    @Value("${app.gateway.server-id}")
    private String serverId;

    public void registerSession(String userId, String sessionId) {
        redis.opsForHash().put(SESSION_PREFIX + userId, sessionId, serverId);
        redis.opsForSet().add(SERVER_PREFIX + serverId, userId);
        redis.expire(SESSION_PREFIX + userId, Duration.ofHours(24));
    }

    public void removeSession(String userId, String sessionId) {
        redis.opsForHash().delete(SESSION_PREFIX + userId, sessionId);
        // 仅当该用户在本实例无剩余会话时移出 server 集合
        if (sessionCount(userId) == 0) {
            redis.opsForSet().remove(SERVER_PREFIX + serverId, userId);
        }
    }

    /** 该用户剩余活跃会话数（多设备时 > 0） */
    public long sessionCount(String userId) {
        Long size = redis.opsForHash().size(SESSION_PREFIX + userId);
        return size == null ? 0 : size;
    }

    /** 优雅关闭：清理本实例登记的全部会话映射（网关主动下线场景） */
    @PreDestroy
    public void gracefulShutdown() {
        String serverKey = SERVER_PREFIX + serverId;
        Set<String> users = redis.opsForSet().members(serverKey);
        if (users == null) {
            return;
        }
        for (String userId : users) {
            Map<Object, Object> sessions = redis.opsForHash().entries(SESSION_PREFIX + userId);
            sessions.forEach((sessionId, owner) -> {
                if (serverId.equals(owner)) {
                    redis.opsForHash().delete(SESSION_PREFIX + userId, sessionId);
                }
            });
        }
        redis.delete(serverKey);
        log.info("网关优雅关闭: serverId={} 清理 {} 个用户会话映射", serverId, users.size());
    }
}
