package com.lin.spring.scheduler.jobapi.dto;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;

import java.time.Instant;

public record JobResponse(
        String jobId,
        JobStatus status,
        String name,
        Instant scheduledTime,
        String cronExpression,
        String payload,
        int retries,
        String meta,
        Instant createdTime,
        Instant modifiedTime
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getJobId(),
                job.getStatus(),
                job.getName(),
                job.getScheduledTime(),
                job.getCronExpression(),
                job.getPayload(),
                job.getRetries(),
                job.getMeta(),
                job.getCreatedTime(),
                job.getModifiedTime()
        );
    }
}
