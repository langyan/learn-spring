package com.lin.spring.orders.payment.repository;

import com.lin.spring.orders.payment.domain.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    @Modifying
    @Query("""
            UPDATE Payment p
               SET p.status = :to, p.providerChargeId = COALESCE(:chargeId, p.providerChargeId),
                   p.failureReason = :reason, p.updatedAt = CURRENT_TIMESTAMP
             WHERE p.id = :id AND p.status = :from
            """)
    int transitionIf(@Param("id") UUID id,
                     @Param("from") Payment.Status from,
                     @Param("to") Payment.Status to,
                     @Param("chargeId") String chargeId,
                     @Param("reason") String reason);

    /** 对账扫描：UNKNOWN 且已超过最小等待时长。 */
    @Query("""
            SELECT p FROM Payment p
             WHERE p.status = 'UNKNOWN' AND p.updatedAt < :threshold
            """)
    List<Payment> findUnknownBefore(@Param("threshold") Instant threshold);

    /** 退款（新的财务操作）：仅 COMPLETED 且未退过款的支付可退（条件更新保证幂等）。 */
    @Modifying
    @Query(value = """
            UPDATE payments
               SET refund_amount = :amount,
                   refund_id = :refundId,
                   updated_at = NOW()
             WHERE id = :id
               AND status = 'COMPLETED'
               AND refund_amount IS NULL
            """, nativeQuery = true)
    int tryRefund(@Param("id") UUID id,
                  @Param("amount") java.math.BigDecimal amount,
                  @Param("refundId") UUID refundId);
}
