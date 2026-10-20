package com.lin.spring.scheduler.jobapi.repository;

import com.lin.spring.scheduler.common.entity.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {

    Optional<JobRun> findFirstByJobIdOrderByAttemptNumberDesc(String jobId);
}
