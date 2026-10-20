package com.lin.spring.scheduler.common.store;

import java.time.Duration;
import java.util.Optional;

/**
 * 高速状态存储抽象：Watcher 的 Last Poll Time 断点、取消标记等 Redis 用途统一走此接口。
 *
 * 实现选择由 profile 决定：
 * - 非 dev：RedisKVStore（真实 Redis，TTL 由 Redis 保证）
 * - dev  ：InMemoryKVStore（无 Docker 快速体验）
 */
public interface KVStore {

    /** 写入键值，ttl 为空或非正数表示永不过期 */
    void put(String key, String value, Duration ttl);

    Optional<String> get(String key);

    boolean hasKey(String key);
}
