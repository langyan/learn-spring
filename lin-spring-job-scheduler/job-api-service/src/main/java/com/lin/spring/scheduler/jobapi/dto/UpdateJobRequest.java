package com.lin.spring.scheduler.jobapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/** 仅 PENDING 状态的作业允许更新；未提供的字段保持原值 */
public record UpdateJobRequest(
        @Size(max = 200) String name,
        Instant scheduledTime,
        @Size(max = 100) String cronExpression,
        @Size(max = 10000) String payload,
        @Min(0) @Max(10) Integer retries,
        @Size(max = 10000) String meta
) {
}
