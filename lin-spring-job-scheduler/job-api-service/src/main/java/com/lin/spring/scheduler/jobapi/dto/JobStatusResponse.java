package com.lin.spring.scheduler.jobapi.dto;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;
import com.lin.spring.scheduler.common.entity.JobRun;

import java.time.Instant;

public record JobStatusResponse(
        String jobId,
        JobStatus status,
        Integer lastAttempt,
        Instant lastRunStart,
        Instant lastRunEnd,
        String lastError,
        String lastExecutorId
) {
    public static JobStatusResponse of(Job job, JobRun lastRun) {
        return new JobStatusResponse(
                job.getJobId(),
                job.getStatus(),
                lastRun == null ? null : lastRun.getAttemptNumber(),
                lastRun == null ? null : lastRun.getStartTime(),
                lastRun == null ? null : lastRun.getEndTime(),
                lastRun == null ? null : lastRun.getErrorMsg(),
                lastRun == null ? null : lastRun.getExecutorId()
        );
    }
}
