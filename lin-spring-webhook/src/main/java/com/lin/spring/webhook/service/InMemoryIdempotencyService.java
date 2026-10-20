package com.lin.spring.webhook.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存幂等存储，供 local profile 与单元测试使用。
 */
@Component
@Profile("local")
public class InMemoryIdempotencyService implements IdempotencyService {

	private final Set<String> claimed = ConcurrentHashMap.newKeySet();
	private final Set<String> done = ConcurrentHashMap.newKeySet();

	@Override
	public boolean tryClaim(String key) {
		if (done.contains(key)) {
			return false;
		}
		return claimed.add(key);
	}

	@Override
	public void markDone(String key) {
		claimed.remove(key);
		done.add(key);
	}

	@Override
	public void release(String key) {
		claimed.remove(key);
	}
}
