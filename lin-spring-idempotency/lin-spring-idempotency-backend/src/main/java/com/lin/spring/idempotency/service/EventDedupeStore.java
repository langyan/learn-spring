package com.lin.spring.idempotency.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class EventDedupeStore {

	private final StringRedisTemplate redisTemplate;
	private final Duration ttl;

	public EventDedupeStore(
		StringRedisTemplate redisTemplate,
		@Value("${app.kafka.processed-event-ttl-hours:24}") long ttlHours
	) {
		this.redisTemplate = redisTemplate;
		this.ttl = Duration.ofHours(ttlHours);
	}

	public boolean claim(String eventId, String orderNo) {
		return Boolean.TRUE.equals(
			redisTemplate.opsForValue().setIfAbsent(redisKey(eventId), orderNo, ttl)
		);
	}

	public boolean isProcessed(String eventId) {
		return Boolean.TRUE.equals(redisTemplate.hasKey(redisKey(eventId)));
	}

	public void release(String eventId) {
		redisTemplate.delete(redisKey(eventId));
	}

	private String redisKey(String eventId) {
		return "processed:event:" + eventId;
	}
}
