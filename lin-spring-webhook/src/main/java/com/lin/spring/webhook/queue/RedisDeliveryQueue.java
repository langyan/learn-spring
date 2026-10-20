package com.lin.spring.webhook.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.webhook.dto.DeliveryJob;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Redis 队列实现：LIST 做主队列（BRPOP/RPUSH），ZSET 做延迟队列（score = 到期时间戳）。
 */
@Slf4j
@Component
@Profile("!local")
public class RedisDeliveryQueue implements DeliveryQueue {

	private static final String READY_KEY = "webhook:q:ready";
	private static final String DELAYED_KEY = "webhook:q:delayed";

	/** 原子地取出已到期的延迟任务，避免多实例重复捞取 */
	private static final DefaultRedisScript<List> DRAIN_SCRIPT = new DefaultRedisScript<>(
		"local members = redis.call('ZRANGEBYSCORE', KEYS[1], '-inf', ARGV[1], 'LIMIT', 0, ARGV[2]) "
			+ "if #members > 0 then redis.call('ZREM', KEYS[1], unpack(members)) end "
			+ "return members",
		List.class);

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper;

	public RedisDeliveryQueue(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
		this.redisTemplate = redisTemplate;
		this.objectMapper = objectMapper;
	}

	@Override
	public void pushReady(DeliveryJob job) {
		redisTemplate.opsForList().rightPush(READY_KEY, serialize(job));
	}

	@Override
	public void pushDelayed(DeliveryJob job, long delaySeconds) {
		double score = Instant.now().plusSeconds(delaySeconds).toEpochMilli();
		redisTemplate.opsForZSet().add(DELAYED_KEY, serialize(job), score);
	}

	@Override
	public List<DeliveryJob> popReady(int maxBatch, Duration timeout) {
		List<DeliveryJob> jobs = new ArrayList<>();
		String first = redisTemplate.opsForList().rightPop(READY_KEY, timeout);
		if (first == null) {
			return jobs;
		}
		jobs.add(deserialize(first));
		while (jobs.size() < maxBatch) {
			String next = redisTemplate.opsForList().rightPop(READY_KEY);
			if (next == null) {
				break;
			}
			jobs.add(deserialize(next));
		}
		return jobs;
	}

	@Override
	public List<DeliveryJob> drainDue(Instant now, int maxBatch) {
		List<String> raws = redisTemplate.execute(
			DRAIN_SCRIPT,
			Collections.singletonList(DELAYED_KEY),
			String.valueOf(now.toEpochMilli()),
			String.valueOf(maxBatch));
		if (raws == null || raws.isEmpty()) {
			return List.of();
		}
		List<DeliveryJob> jobs = new ArrayList<>(raws.size());
		for (String raw : raws) {
			jobs.add(deserialize(raw));
		}
		return jobs;
	}

	@Override
	public long readyDepth() {
		Long size = redisTemplate.opsForList().size(READY_KEY);
		return size == null ? 0 : size;
	}

	@Override
	public long delayedDepth() {
		Long size = redisTemplate.opsForZSet().size(DELAYED_KEY);
		return size == null ? 0 : size;
	}

	private String serialize(DeliveryJob job) {
		try {
			return objectMapper.writeValueAsString(job);
		} catch (Exception ex) {
			throw new IllegalStateException("投递任务序列化失败", ex);
		}
	}

	private DeliveryJob deserialize(String raw) {
		try {
			return objectMapper.readValue(raw, DeliveryJob.class);
		} catch (Exception ex) {
			log.error("投递任务反序列化失败，丢弃: {}", raw, ex);
			throw new IllegalStateException("投递任务反序列化失败", ex);
		}
	}
}
