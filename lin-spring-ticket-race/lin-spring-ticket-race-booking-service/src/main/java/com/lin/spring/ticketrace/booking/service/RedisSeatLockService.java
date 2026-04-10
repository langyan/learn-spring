package com.lin.spring.ticketrace.booking.service;

import com.lin.spring.ticketrace.booking.exception.StrategyUnavailableException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RedisSeatLockService {

    private final StringRedisTemplate stringRedisTemplate;

    public boolean acquire(String redisKey, String bookingNo, Duration ttl) {
        try {
            Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(redisKey, bookingNo, ttl);
            return Boolean.TRUE.equals(locked);
        } catch (DataAccessException exception) {
            throw new StrategyUnavailableException("Redis hold strategy is unavailable: " + exception.getMessage());
        }
    }

    public void release(String redisKey) {
        if (redisKey == null || redisKey.isBlank()) {
            return;
        }
        try {
            stringRedisTemplate.delete(redisKey);
        } catch (DataAccessException exception) {
            throw new StrategyUnavailableException("Redis hold release failed: " + exception.getMessage());
        }
    }
}
