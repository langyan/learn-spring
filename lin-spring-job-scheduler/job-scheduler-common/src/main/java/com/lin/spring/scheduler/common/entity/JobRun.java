package com.lin.spring.scheduler.common.entity;

import com.lin.spring.scheduler.common.domain.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 作业执行日志表（与 Job 定义表分离）。
 *
 * - modified_time 兼作执行器心跳：执行期间 Executor 每 10s 更新一次；
 *   Watcher 僵尸检测据此判断执行节点是否已死。
 * - (job_id, attempt_number) 唯一约束保证至少一次语义下的幂等：
 *   Kafka 重复投递不会产生重复执行记录。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "job_run",
        uniqueConstraints = @UniqueConstraint(name = "uk_job_attempt", columnNames = {"job_id", "attempt_number"}),
        indexes = {
                @Index(name = "idx_jobrun_status_modified", columnList = "status, modified_time"),
                @Index(name = "idx_jobrun_job", columnList = "job_id")
        })
public class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private JobStatus status;

    @Column(name = "start_time")
    private Instant startTime;

    @Column(name = "end_time")
    private Instant endTime;

    @Column(name = "modified_time", nullable = false)
    private Instant modifiedTime;

    @Column(name = "executor_id", length = 100)
    private String executorId;

    @Column(name = "error_msg", length = 10000)
    private String errorMsg;
}
