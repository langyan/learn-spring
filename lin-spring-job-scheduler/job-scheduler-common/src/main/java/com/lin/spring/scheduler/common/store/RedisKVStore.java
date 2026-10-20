package com.lin.spring.scheduler.common.store;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * 基于 Redis 的实现（默认/mysql profile）。
 * 取消标记 key：JOB_{jobId}_CANCEL，TTL 30 秒（Redis TTL 技巧，见 README）。
 */
public class RedisKVStore implements KVStore {

    private final StringRedisTemplate redis;

    public RedisKVStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public void put(String key, String value, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            redis.opsForValue().set(key, value);
        } else {
            redis.opsForValue().set(key, value, ttl);
        }
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    @Override
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redis.hasKey(key));
    }
}
