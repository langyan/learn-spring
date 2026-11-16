package com.lin.spring.orders.inventory.service;

import com.lin.spring.orders.inventory.domain.InventoryReservation;
import com.lin.spring.orders.inventory.repository.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 预留过期回收：支付长时间未决时，库存不能被无限占用。
 * 部分索引（status='RESERVED' AND expires_at）支撑扫描；释放路径复用幂等 releaseForOrder。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpiryJob {

    private final InventoryReservationRepository reservationRepository;
    private final InventoryReservationService reservationService;

    @Scheduled(fixedDelayString = "${app.reservation.expiry-scan-fixed-delay-ms}")
    public void expireReservations() {
        var expired = reservationRepository.findExpired(Instant.now());
        if (expired.isEmpty()) {
            return;
        }
        Set<UUID> orderIds = new LinkedHashSet<>();
        expired.forEach(r -> orderIds.add(r.getOrderId()));
        log.warn("Expiring {} reservation line(s) across {} order(s)", expired.size(), orderIds.size());
        orderIds.forEach(orderId ->
                reservationService.releaseForOrder(orderId, null, "RESERVATION_EXPIRED"));
    }
}
