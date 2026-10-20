package com.lin.spring.scheduler.watcher.repository;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface JobRepository extends JpaRepository<Job, String> {

    /**
     * 轮询核心查询（mysql profile）：SELECT ... FOR UPDATE SKIP LOCKED。
     * - 走 (status, scheduled_time) 复合索引，避免全表扫描拖垮数据库；
     * - SKIP LOCKED 保证多 Watcher 实例水平扩展互不阻塞、不重复派发。
     * 拉取窗口 [from, to] = [上次轮询时间, now + pollAhead]。
     */
    @Query(value = """
            SELECT job_id FROM job
             WHERE status = 'PENDING'
               AND scheduled_time >= :from
               AND scheduled_time <= :to
             ORDER BY scheduled_time
             LIMIT :limit
             FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<String> lockDueJobIds(@Param("from") Instant from,
                               @Param("to") Instant to,
                               @Param("limit") int limit);

    /** dev profile（H2 不支持 SKIP LOCKED）的等价无锁查询 */
    List<String> findJobIdByStatusAndScheduledTimeBetweenOrderByScheduledTime(
            JobStatus status, Instant from, Instant to, Pageable pageable);

    /** 批量置 QUEUED（WHERE status=PENDING 双保险，保证仅统计真正被本实例抢到的作业） */
    @Modifying
    @Query("""
            update Job j
               set j.status = com.lin.spring.scheduler.common.domain.JobStatus.QUEUED,
                   j.modifiedTime = :now
             where j.jobId in :jobIds
               and j.status = com.lin.spring.scheduler.common.domain.JobStatus.PENDING
            """)
    int markQueued(@Param("jobIds") List<String> jobIds, @Param("now") Instant now);
}
