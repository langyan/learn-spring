package com.lin.spring.scheduler.common.entity;

import com.lin.spring.scheduler.common.domain.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 作业定义表（定义与执行日志分离，避免执行日志膨胀拖慢调度查询）。
 *
 * 复合索引 (status, scheduled_time)：Watcher 轮询 "PENDING 且 scheduled_time 在窗口内"
 * 的查询走索引，避免大规模全表扫描导致数据库崩溃。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "job", indexes = {
        @Index(name = "idx_job_status_time", columnList = "status, scheduled_time")
})
public class Job {

    @Id
    @Column(name = "job_id", length = 64)
    private String jobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private JobStatus status;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "scheduled_time", nullable = false)
    private Instant scheduledTime;

    /** 仅存储，本演示不解析 cron 表达式（不自动计算下一次触发时间） */
    @Column(name = "cron_expression", length = 100)
    private String cronExpression;

    @Column(name = "payload", length = 10000)
    private String payload;

    /** 最大重试次数（attemptNumber 从 0 开始，超过 maxRetries 进入死信队列） */
    @Column(name = "retries", nullable = false)
    private int retries = 3;

    @Column(name = "meta", length = 10000)
    private String meta;

    @Column(name = "created_time", nullable = false, updatable = false)
    private Instant createdTime;

    @Column(name = "modified_time", nullable = false)
    private Instant modifiedTime;
}
