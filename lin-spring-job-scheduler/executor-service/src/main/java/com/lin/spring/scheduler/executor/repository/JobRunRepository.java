package com.lin.spring.scheduler.executor.repository;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.JobRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface JobRunRepository extends JpaRepository<JobRun, Long> {

    /** 幂等检查：至少一次语义下 Kafka 可能重复投递同一 (jobId, attempt) */
    Optional<JobRun> findByJobIdAndAttemptNumber(String jobId, int attemptNumber);

    /** 心跳：执行期间每 10s 更新一次 modified_time，供 Watcher 僵尸侦测 */
    @Modifying
    @Query("""
            update JobRun r
               set r.modifiedTime = :now
             where r.id = :id
            """)
    int touchHeartbeat(@Param("id") Long id, @Param("now") Instant now);

    /** 执行完成/失败的最终回写 */
    @Modifying
    @Query("""
            update JobRun r
               set r.status = :status, r.endTime = :now, r.modifiedTime = :now, r.errorMsg = :errorMsg
             where r.id = :id
            """)
    int finishRun(@Param("id") Long id,
                  @Param("status") JobStatus status,
                  @Param("now") Instant now,
                  @Param("errorMsg") String errorMsg);
}
