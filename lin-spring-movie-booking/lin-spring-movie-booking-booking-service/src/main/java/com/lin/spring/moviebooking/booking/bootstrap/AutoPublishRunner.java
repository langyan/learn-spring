package com.lin.spring.moviebooking.booking.bootstrap;

import com.lin.spring.moviebooking.booking.client.MovieCatalogClient;
import com.lin.spring.moviebooking.booking.service.ShowInventoryService;
import com.lin.spring.moviebooking.common.dto.ShowResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Demo convenience: publish every show in the catalog on first boot so the
 * seat maps are populated. In production this would be triggered by the
 * show-publishing workflow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoPublishRunner implements ApplicationRunner {

    private final MovieCatalogClient movieCatalogClient;
    private final ShowInventoryService showInventoryService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<ShowResponse> shows = movieCatalogClient.fetchAllShows();
            int published = 0;
            for (ShowResponse show : shows) {
                published += showInventoryService.publishShow(show.id());
            }
            log.info("Auto-published inventory for {} shows ({} new seat sets)",
                    shows.size(), published);
        } catch (Exception ex) {
            log.warn("Auto-publish skipped (movie-service not reachable yet?): {}",
                    ex.getMessage());
        }
    }
}
