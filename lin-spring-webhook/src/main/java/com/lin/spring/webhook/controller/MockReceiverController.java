package com.lin.spring.webhook.controller;

import com.lin.spring.webhook.delivery.SignatureSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 可注入故障的 Mock 商户接收端，用来复现面试追问 1、2、4。
 *
 * <p>模式：
 * <ul>
 *   <li>{@code ok} —— 立即返回 200</li>
 *   <li>{@code fail500} —— 恒定返回 500（追问 1）</li>
 *   <li>{@code hang30s} —— 接受连接后挂起 30 秒（追问 2 的慢商户）</li>
 *   <li>{@code slow} —— 睡 3 秒后返回 200</li>
 *   <li>{@code idempotent} —— 按事件 ID 去重，演示接收端幂等（追问 4）</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/mock")
public class MockReceiverController {

	private final SignatureSigner signatureSigner;

	/** 已处理过的事件 ID —— 接收端幂等的全部秘密 */
	private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
	private final Map<String, AtomicLong> receivedByMode = new ConcurrentHashMap<>();
	private final AtomicInteger duplicatesIgnored = new AtomicInteger();

	public MockReceiverController(SignatureSigner signatureSigner) {
		this.signatureSigner = signatureSigner;
	}

	@PostMapping("/receiver/{mode}")
	public ResponseEntity<Map<String, Object>> receive(
		@PathVariable String mode,
		@RequestBody(required = false) String body,
		@RequestHeader(value = "X-Webhook-Id", required = false) String eventId,
		@RequestHeader(value = "X-Webhook-Signature", required = false) String signature
	) {
		receivedByMode.computeIfAbsent(mode, key -> new AtomicLong()).incrementAndGet();
		String payload = body == null ? "" : body;

		switch (mode) {
			case "ok" -> {
				log.info("[mock:ok] 收到 eventId={} 签名有效={}", eventId, signature != null);
				return ResponseEntity.ok(Map.of("status", "ok", "eventId", eventId));
			}
			case "fail500" -> {
				log.info("[mock:fail500] 返回 500, eventId={}", eventId);
				return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(Map.of("status", "error", "eventId", eventId));
			}
			case "hang30s" -> {
				// 模拟「配置不足、接受连接后挂死」的商户服务器
				sleepQuietly(30_000);
				return ResponseEntity.ok(Map.of("status", "ok_after_30s", "eventId", eventId));
			}
			case "slow" -> {
				sleepQuietly(3_000);
				return ResponseEntity.ok(Map.of("status", "ok_slow", "eventId", eventId));
			}
			case "idempotent" -> {
				if (eventId == null) {
					return ResponseEntity.badRequest().body(Map.of("status", "missing_event_id"));
				}
				boolean firstTime = processedEventIds.add(eventId);
				if (!firstTime) {
					// 重复投递被安全忽略：这就是会计系统不会记两次收入的原因
					duplicatesIgnored.incrementAndGet();
					log.info("[mock:idempotent] 重复事件已忽略 eventId={}", eventId);
					return ResponseEntity.ok(Map.of("status", "duplicate_ignored", "eventId", eventId));
				}
				return ResponseEntity.ok(Map.of("status", "processed", "eventId", eventId));
			}
			default -> {
				return ResponseEntity.badRequest().body(Map.of("status", "unknown_mode", "mode", mode));
			}
		}
	}

	/**
	 * 演示商户侧如何校验签名（可选能力，与投递可靠性无关）。
	 */
	@GetMapping("/verify")
	public Map<String, Object> verify(String payload, String secret, String signature) {
		return Map.of("valid", signatureSigner.verify(payload, secret, signature));
	}

	@GetMapping("/stats")
	public Map<String, Object> stats() {
		return Map.of(
			"receivedByMode", receivedByMode,
			"processedEventIds", processedEventIds.size(),
			"duplicatesIgnored", duplicatesIgnored.get());
	}

	@PostMapping("/reset")
	public Map<String, Object> reset() {
		processedEventIds.clear();
		receivedByMode.clear();
		duplicatesIgnored.set(0);
		return Map.of("status", "reset");
	}

	private void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}
}
