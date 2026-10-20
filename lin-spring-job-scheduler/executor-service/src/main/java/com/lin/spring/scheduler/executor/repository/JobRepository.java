package com.lin.spring.scheduler.executor.repository;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface JobRepository extends JpaRepository<Job, String> {

    /** 无条件状态回写（执行结果优先于其他中间态） */
    @Modifying
    @Query("""
            update Job j
               set j.status = :status, j.modifiedTime = :now
             where j.jobId = :jobId
            """)
    int updateStatus(@Param("jobId") String jobId,
                     @Param("status") JobStatus status,
                     @Param("now") Instant now);
}
