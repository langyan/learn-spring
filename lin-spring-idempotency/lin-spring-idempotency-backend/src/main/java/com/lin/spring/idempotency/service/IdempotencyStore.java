package com.lin.spring.idempotency.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.idempotency.dto.OrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

@Service
public class IdempotencyStore {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;
	private final Duration ttl;

	public IdempotencyStore(
		StringRedisTemplate redisTemplate,
		ObjectMapper objectMapper,
		@Value("${app.idempotency.ttl-hours:24}") long ttlHours
	) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
		this.ttl = Duration.ofHours(ttlHours);
	}

	public IdempotencyDecision claim(String idempotencyKey, String requestHash) {
		String redisKey = redisKey(idempotencyKey);
		IdempotencyRecord processingRecord = new IdempotencyRecord(
			IdempotencyStatus.PROCESSING,
			requestHash,
			null,
			null,
			Instant.now()
		);
		Boolean claimed = redisTemplate.opsForValue().setIfAbsent(redisKey, serialize(processingRecord), ttl);
		if (Boolean.TRUE.equals(claimed)) {
			return IdempotencyDecision.claimed();
		}

		String rawRecord = redisTemplate.opsForValue().get(redisKey);
		if (rawRecord == null) {
			return claim(idempotencyKey, requestHash);
		}

		IdempotencyRecord existingRecord = deserialize(rawRecord);
		if (!Objects.equals(existingRecord.requestHash(), requestHash)) {
			return IdempotencyDecision.conflict();
		}
		if (existingRecord.status() == IdempotencyStatus.COMPLETED && existingRecord.responseBody() != null) {
			return IdempotencyDecision.completed(existingRecord.responseBody());
		}
		return IdempotencyDecision.processing();
	}

	public void markCompleted(String idempotencyKey, String requestHash, OrderResponse response) {
		IdempotencyRecord completedRecord = new IdempotencyRecord(
			IdempotencyStatus.COMPLETED,
			requestHash,
			response,
			response.orderNo(),
			Instant.now()
		);
		redisTemplate.opsForValue().set(redisKey(idempotencyKey), serialize(completedRecord), ttl);
	}

	public void releaseProcessing(String idempotencyKey) {
		redisTemplate.delete(redisKey(idempotencyKey));
	}

	private String redisKey(String idempotencyKey) {
		return "idempotency:order:" + idempotencyKey;
	}

	private String serialize(IdempotencyRecord record) {
		try {
			return objectMapper.writeValueAsString(record);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to serialize idempotency record", ex);
		}
	}

	private IdempotencyRecord deserialize(String record) {
		try {
			return objectMapper.readValue(record, IdempotencyRecord.class);
		} catch (JsonProcessingException ex) {
			throw new IllegalStateException("Failed to deserialize idempotency record", ex);
		}
	}
}
