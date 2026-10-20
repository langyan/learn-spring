package com.lin.spring.webhook.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 平台侧事件级幂等（追问 4 的前半部分）。
 */
class IdempotencyTest {

	private final IdempotencyService idempotency = new InMemoryIdempotencyService();

	@Test
	void 同一事件并发投递只会处理一次() {
		assertTrue(idempotency.tryClaim("evt_1:ep_1"));
		assertFalse(idempotency.tryClaim("evt_1:ep_1"), "第二次抢占必须失败");
	}

	@Test
	void 释放后可以重试() {
		assertTrue(idempotency.tryClaim("evt_2:ep_1"));
		idempotency.release("evt_2:ep_1");
		assertTrue(idempotency.tryClaim("evt_2:ep_1"), "可重试失败释放后应允许再次尝试");
	}

	@Test
	void 终态之后永久忽略() {
		assertTrue(idempotency.tryClaim("evt_3:ep_1"));
		idempotency.markDone("evt_3:ep_1");
		assertFalse(idempotency.tryClaim("evt_3:ep_1"));
		assertFalse(idempotency.tryClaim("evt_3:ep_1"));
	}
}
