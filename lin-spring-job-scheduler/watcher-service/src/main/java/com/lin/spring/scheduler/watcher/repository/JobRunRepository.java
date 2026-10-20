package com.lin.spring.scheduler.watcher.repository;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {

    /**
     * 僵尸侦测：RUNNING 但心跳（modified_time）超过阈值未更新的执行记录，
     * 判定执行节点已死。走 (status, modified_time) 索引。
     */
    List<JobRun> findByStatusAndModifiedTimeBefore(JobStatus status, Instant threshold);

    /** 僵尸回收后关闭对应执行记录 */
    @Modifying
    @Query("""
            update JobRun r
               set r.status = :toStatus, r.endTime = :now, r.modifiedTime = :now,
                   r.errorMsg = :errorMsg
             where r.id = :id
               and r.status = com.lin.spring.scheduler.common.domain.JobStatus.RUNNING
            """)
    int closeStaleRun(@Param("id") Long id,
                      @Param("toStatus") JobStatus toStatus,
                      @Param("now") Instant now,
                      @Param("errorMsg") String errorMsg);
}
