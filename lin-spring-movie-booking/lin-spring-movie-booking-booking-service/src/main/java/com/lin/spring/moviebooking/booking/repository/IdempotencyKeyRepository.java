package com.lin.spring.moviebooking.booking.repository;

import com.lin.spring.moviebooking.booking.domain.IdempotencyKey;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.QueryHints;

import java.util.Optional;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, Long> {

    /**
     * PESSIMISTIC_WRITE on the fast path is deliberate: two concurrent requests
     * with the same key must not both pass the miss branch. The unique
     * constraint remains the backstop if the read races anyway.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
    Optional<IdempotencyKey> findByUserIdAndIdemKeyAndEndpointForUpdate(long userId, String idemKey, String endpoint);

    Optional<IdempotencyKey> findByUserIdAndIdemKeyAndEndpoint(long userId, String idemKey, String endpoint);
}
