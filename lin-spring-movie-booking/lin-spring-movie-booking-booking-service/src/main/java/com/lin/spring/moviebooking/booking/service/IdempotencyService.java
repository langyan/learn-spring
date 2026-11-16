package com.lin.spring.moviebooking.booking.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lin.spring.moviebooking.booking.domain.IdempotencyKey;
import com.lin.spring.moviebooking.booking.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Response cache keyed by (user, key, endpoint). The cached response is
 * written in the SAME transaction as the business change, so a rollback
 * rolls the cache entry back too — no cache entry for a state change
 * that never happened.
 */
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRED)
    public <T> T findCached(long userId, String idemKey, String endpoint, Class<T> type) {
        return repository.findByUserIdAndIdemKeyAndEndpoint(userId, idemKey, endpoint)
                .map(k -> deserialize(k.getResponsePayload(), type))
                .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void remember(long userId, String idemKey, String endpoint, Object response) {
        IdempotencyKey key = new IdempotencyKey();
        key.setUserId(userId);
        key.setIdemKey(idemKey);
        key.setEndpoint(endpoint);
        key.setResponsePayload(serialize(response));
        key.setCreatedAt(java.time.Instant.now());
        repository.save(key);
    }

    private String serialize(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (Exception ex) {
            throw new IllegalStateException("Idempotency serialization failed", ex);
        }
    }

    private <T> T deserialize(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Idempotency deserialization failed", ex);
        }
    }
}
