package com.lin.spring.moviebooking.payment.store;

import com.lin.spring.moviebooking.payment.domain.PaymentIntent;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Intent storage + creation idempotency: the same (booking, idemKey) always
 * returns the same intent — double-tapped payment buttons cannot create two
 * charges at the gateway.
 */
@Component
public class IntentStore {

    private final ConcurrentHashMap<String, PaymentIntent> byGatewayRef = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> idemIndex = new ConcurrentHashMap<>();

    public PaymentIntent getOrCreate(String idempotencyKey, java.util.function.Supplier<PaymentIntent> factory) {
        String existingRef = idemIndex.get(idempotencyKey);
        if (existingRef != null) {
            PaymentIntent existing = byGatewayRef.get(existingRef);
            if (existing != null) {
                return existing;
            }
        }
        PaymentIntent created = factory.get();
        PaymentIntent winner = byGatewayRef.putIfAbsent(created.getGatewayRef(), created);
        if (winner != null) {
            return winner;
        }
        idemIndex.put(idempotencyKey, created.getGatewayRef());
        return created;
    }

    public Optional<PaymentIntent> findByGatewayRef(String gatewayRef) {
        return Optional.ofNullable(byGatewayRef.get(gatewayRef));
    }
}
