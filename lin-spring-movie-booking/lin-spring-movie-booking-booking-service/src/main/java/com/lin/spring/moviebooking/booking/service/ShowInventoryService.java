package com.lin.spring.moviebooking.booking.service;

import com.lin.spring.moviebooking.booking.client.MovieCatalogClient;
import com.lin.spring.moviebooking.booking.domain.ShowSeat;
import com.lin.spring.moviebooking.booking.domain.ShowSeatId;
import com.lin.spring.moviebooking.booking.repository.ShowSeatRepository;
import com.lin.spring.moviebooking.common.dto.SeatLayoutItem;
import com.lin.spring.moviebooking.common.dto.SeatLayoutResponse;
import com.lin.spring.moviebooking.common.dto.SeatStatusItem;
import com.lin.spring.moviebooking.common.enums.SeatStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Publishes show inventory: bulk-creates the show_seats hot rows from the
 * catalog's static layout, and serves authoritative seat statuses for the
 * seat-map assembler in movie-service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShowInventoryService {

    private final ShowSeatRepository showSeatRepository;
    private final MovieCatalogClient movieCatalogClient;

    /** Idempotent: re-publishing an existing show is a no-op. */
    @Transactional
    public int publishShow(long showId) {
        if (showSeatRepository.existsByIdShowId(showId)) {
            log.info("Show {} already published", showId);
            return 0;
        }
        SeatLayoutResponse layout = movieCatalogClient.fetchLayout(showId);
        Instant now = Instant.now();
        List<ShowSeat> rows = new ArrayList<>(layout.seats().size());
        for (SeatLayoutItem item : layout.seats()) {
            ShowSeat seat = new ShowSeat();
            seat.setId(new ShowSeatId(showId, item.seatId()));
            seat.setStatus(SeatStatus.AVAILABLE);
            seat.setPrice(layout.basePrice().add(item.priceAdjust() == null
                    ? BigDecimal.ZERO : item.priceAdjust()));
            seat.setVersion(0);
            seat.setUpdatedAt(now);
            rows.add(seat);
        }
        showSeatRepository.saveAll(rows);
        log.info("Published show {} with {} seat rows (basePrice={})",
                showId, rows.size(), layout.basePrice());
        return rows.size();
    }

    @Transactional(readOnly = true)
    public List<SeatStatusItem> seatStatuses(long showId) {
        return showSeatRepository.findByIdShowIdOrderByIdSeatId(showId).stream()
                .map(s -> new SeatStatusItem(s.getId().getSeatId(), s.getStatus(), s.getPrice()))
                .toList();
    }
}
