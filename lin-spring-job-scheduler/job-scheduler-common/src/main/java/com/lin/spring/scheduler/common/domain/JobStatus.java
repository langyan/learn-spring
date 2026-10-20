package com.lin.spring.scheduler.common.domain;

/**
 * 作业生命周期状态。
 * PENDING -> QUEUED -> RUNNING -> COMPLETED / FAILED / CANCELLED
 */
public enum JobStatus {
    PENDING,
    QUEUED,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == CANCELLED || this == FAILED;
    }
}
