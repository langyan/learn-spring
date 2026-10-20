package com.lin.spring.webhook.service;

import com.lin.spring.webhook.config.WebhookProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis SETNX 的幂等存储。
 */
@Component
@Profile("!local")
public class RedisIdempotencyService implements IdempotencyService {

	private static final String KEY_PREFIX = "webhook:idem:";

	private final StringRedisTemplate redisTemplate;
	private final Duration ttl;

	public RedisIdempotencyService(StringRedisTemplate redisTemplate, WebhookProperties properties) {
		this.redisTemplate = redisTemplate;
		this.ttl = Duration.ofHours(properties.getIdempotency().getTtlHours());
	}

	@Override
	public boolean tryClaim(String key) {
		return Boolean.TRUE.equals(
			redisTemplate.opsForValue().setIfAbsent(KEY_PREFIX + key, "PROCESSING", ttl));
	}

	@Override
	public void markDone(String key) {
		redisTemplate.opsForValue().set(KEY_PREFIX + key, "DONE", ttl);
	}

	@Override
	public void release(String key) {
		redisTemplate.delete(KEY_PREFIX + key);
	}
}
