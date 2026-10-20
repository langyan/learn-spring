package com.lin.spring.scheduler.jobapi.repository;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;

public interface JobRepository extends JpaRepository<Job, String> {

    /**
     * 条件状态更新：仅当作业仍处于 fromStatuses 之一时才更新，避免已取消/已完成的作业被覆盖。
     * 返回受影响行数（0 表示状态已变化，调用方按并发冲突处理）。
     */
    @Modifying
    @Query("""
            update Job j
               set j.status = :toStatus, j.modifiedTime = :now
             where j.jobId = :jobId
               and j.status in :fromStatuses
            """)
    int updateStatusIfIn(@Param("jobId") String jobId,
                         @Param("fromStatuses") Collection<JobStatus> fromStatuses,
                         @Param("toStatus") JobStatus toStatus,
                         @Param("now") Instant now);
}
