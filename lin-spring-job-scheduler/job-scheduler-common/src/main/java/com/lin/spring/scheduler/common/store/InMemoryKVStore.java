package com.lin.spring.scheduler.common.store;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * dev profile（无 Docker 快速体验）的内存实现：TTL 靠过期时间戳惰性判断。
 */
public class InMemoryKVStore implements KVStore {

    private record Entry(String value, Instant expiresAt) {
    }

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public void put(String key, String value, Duration ttl) {
        Instant expiresAt = (ttl == null || ttl.isZero() || ttl.isNegative())
                ? Instant.MAX
                : Instant.now().plus(ttl);
        store.put(key, new Entry(value, expiresAt));
    }

    @Override
    public Optional<String> get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(key, entry);
            return Optional.empty();
        }
        return Optional.of(entry.value());
    }

    @Override
    public boolean hasKey(String key) {
        return get(key).isPresent();
    }
}
