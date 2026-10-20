package com.lin.spring.notification.idempotency;

import com.lin.spring.notification.config.NotificationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis SETNX 幂等存储，实现消费端有效一次投递。
 */
@Component
@Profile("kafka")
public class RedisNotificationDedupeStore implements NotificationDedupeStore {

	private static final String KEY_PREFIX = "notification:idem:";

	private final StringRedisTemplate redisTemplate;
	private final Duration ttl;

	public RedisNotificationDedupeStore(
		StringRedisTemplate redisTemplate,
		NotificationProperties properties
	) {
		this.redisTemplate = redisTemplate;
		this.ttl = Duration.ofHours(properties.getIdempotency().getTtlHours());
	}

	@Override
	public boolean tryClaim(String idempotencyKey) {
		return Boolean.TRUE.equals(
			redisTemplate.opsForValue().setIfAbsent(key(idempotencyKey), "PROCESSING", ttl)
		);
	}

	@Override
	public void markDone(String idempotencyKey) {
		redisTemplate.opsForValue().set(key(idempotencyKey), "DONE", ttl);
	}

	@Override
	public void release(String idempotencyKey) {
		redisTemplate.delete(key(idempotencyKey));
	}

	private String key(String idempotencyKey) {
		return KEY_PREFIX + idempotencyKey;
	}
}
