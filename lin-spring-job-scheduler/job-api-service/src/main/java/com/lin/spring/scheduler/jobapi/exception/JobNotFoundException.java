package com.lin.spring.scheduler.jobapi.exception;

public class JobNotFoundException extends RuntimeException {

    public JobNotFoundException(String jobId) {
        super("Job not found: " + jobId);
    }
}
