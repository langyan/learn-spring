package com.lin.spring.moviebooking.common.dto;

public record MovieSummaryResponse(
        Long id,
        String title,
        String language,
        String genre,
        int durationMinutes
) {}
