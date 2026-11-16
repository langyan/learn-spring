package com.lin.spring.moviebooking.booking.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collection;
import java.util.List;

/**
 * Layer 1 — Redis advisory holds. Best effort, NON-authoritative.
 *
 * <p>SET NX PX collapses the ~90% of contention where everyone chases an
 * already-taken seat at sub-millisecond cost. If Redis is down we log and
 * continue — MySQL is the authority. Release uses compare-and-delete Lua so
 * a naive DEL after TTL expiry can never delete a lock someone else acquired.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeatLockService {

    private static final String KEY_PREFIX = "seat:hold:";

    /** Compare-and-delete: only remove the key if it still maps to OUR holdId. */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            else
                return 0
            end
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * Tries to acquire advisory locks for all seats.
     *
     * @return false when at least one seat is already locked (likely conflict —
     * short-circuit), true when all acquired OR when Redis is unavailable
     * (degrade to MySQL-only; correctness unaffected).
     */
    public boolean tryAcquire(long showId, Collection<Long> seatIds, String holdId, Duration ttl) {
        try {
            for (Long seatId : seatIds) {
                String key = KEY_PREFIX + showId + ":" + seatId;
                Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, holdId, ttl);
                if (!Boolean.TRUE.equals(ok)) {
                    // Roll back the seats we did get, then report the conflict.
                    release(showId, seatIds, holdId);
                    return false;
                }
            }
            return true;
        } catch (Exception ex) {
            log.warn("Redis advisory lock unavailable, degrading to MySQL-only path: {}",
                    ex.getMessage());
            return true;
        }
    }

    /** Compare-and-delete release; any Redis failure is logged and ignored. */
    public void release(long showId, Collection<Long> seatIds, String holdId) {
        try {
            List<String> keys = seatIds.stream()
                    .map(seatId -> KEY_PREFIX + showId + ":" + seatId)
                    .toList();
            for (String key : keys) {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), holdId);
            }
        } catch (Exception ex) {
            log.warn("Redis advisory lock release failed (keys expire anyway): {}",
                    ex.getMessage());
        }
    }
}
