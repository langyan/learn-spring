package com.lin.spring.scheduler.jobapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record CreateJobRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull Instant scheduledTime,
        @Size(max = 100) String cronExpression,
        @Size(max = 10000) String payload,
        @Min(0) @Max(10) Integer retries,
        @Size(max = 10000) String meta
) {
}
