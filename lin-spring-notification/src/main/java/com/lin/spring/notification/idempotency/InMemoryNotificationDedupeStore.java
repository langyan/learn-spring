package com.lin.spring.notification.idempotency;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存幂等存储，用于默认本地模式（无 Redis）。
 */
@Component
@Profile("!kafka")
public class InMemoryNotificationDedupeStore implements NotificationDedupeStore {

	private final Map<String, String> store = new ConcurrentHashMap<>();

	@Override
	public boolean tryClaim(String idempotencyKey) {
		return store.putIfAbsent(idempotencyKey, "PROCESSING") == null;
	}

	@Override
	public void markDone(String idempotencyKey) {
		store.put(idempotencyKey, "DONE");
	}

	@Override
	public void release(String idempotencyKey) {
		store.remove(idempotencyKey);
	}
}
