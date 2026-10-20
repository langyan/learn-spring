package com.lin.spring.scheduler.executor.service;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.JobRun;
import com.lin.spring.scheduler.common.messaging.JobMessage;
import com.lin.spring.scheduler.common.messaging.KafkaTopics;
import com.lin.spring.scheduler.common.store.KVStore;
import com.lin.spring.scheduler.common.util.JsonCodec;
import com.lin.spring.scheduler.executor.handler.JobHandler;
import com.lin.spring.scheduler.executor.repository.JobRepository;
import com.lin.spring.scheduler.executor.repository.JobRunRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.UUID;

/**
 * 执行核心链路（文章语义）：
 * 1. Redis 取消 pre-flight 检查 —— 命中 JOB_{id}_CANCEL 即刻截停；
 * 2. 幂等创建 JobRun（(jobId, attempt) 唯一约束），Kafka 重复投送直接跳过；
 * 3. 执行期间 10s 心跳；
 * 4. 成功回写 COMPLETED；瞬时失败回写 FAILED 并按 attempt 路由 jobs.retry / jobs.dead。
 */
@Slf4j
@Service
public class JobExecutionService {

    private final JobRunRepository jobRunRepository;
    private final JobRepository jobRepository;
    private final HeartbeatService heartbeatService;
    private final JobHandler jobHandler;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KVStore kvStore;
    private final String executorId;

    @Value("${scheduler.retry-backoff-ms:0}")
    private long retryBackoffMs;

    public JobExecutionService(JobRunRepository jobRunRepository,
                               JobRepository jobRepository,
                               HeartbeatService heartbeatService,
                               JobHandler jobHandler,
                               KafkaTemplate<String, String> kafkaTemplate,
                               KVStore kvStore) {
        this.jobRunRepository = jobRunRepository;
        this.jobRepository = jobRepository;
        this.heartbeatService = heartbeatService;
        this.jobHandler = jobHandler;
        this.kafkaTemplate = kafkaTemplate;
        this.kvStore = kvStore;
        this.executorId = resolveExecutorId();
    }

    private static String resolveExecutorId() {
        try {
            return InetAddress.getLocalHost().getHostName() + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (UnknownHostException e) {
            return "executor-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    public void execute(JobMessage message) {
        // ---- 1. 取消 pre-flight 检查（Redis 快速通道，文章原文语义）----
        String cancelKey = "JOB_" + message.jobId() + "_CANCEL";
        if (kvStore.hasKey(cancelKey)) {
            log.info("Job {} was cancelled mid-flight. Halting execution.", message.jobId());
            jobRepository.updateStatus(message.jobId(), JobStatus.CANCELLED, Instant.now());
            return;
        }

        // ---- 2. 幂等检查：同一 (jobId, attempt) 已有执行记录则跳过（至少一次语义的去重）----
        if (jobRunRepository.findByJobIdAndAttemptNumber(message.jobId(), message.attemptNumber()).isPresent()) {
            log.info("Job {} attempt {} already processed, skipping duplicate delivery",
                    message.jobId(), message.attemptNumber());
            return;
        }

        Instant now = Instant.now();
        JobRun run = createRun(message, now);
        if (run == null) {
            return;
        }
        jobRepository.updateStatus(message.jobId(), JobStatus.RUNNING, now);

        // ---- 3. 执行 + 心跳 ----
        try (AutoCloseable ignored = heartbeatService.start(run.getId())) {
            jobHandler.execute(message);
            finish(run.getId(), message.jobId(), JobStatus.COMPLETED, null);
            log.debug("Job {} attempt {} completed by {}", message.jobId(), message.attemptNumber(), executorId);
        } catch (Exception e) {
            finish(run.getId(), message.jobId(), JobStatus.FAILED, e.getMessage());
            routeAfterFailure(message, e);
        }
    }

    /** 创建执行记录；并发重复投送时唯一约束兜底（返回 null 表示被别处抢先） */
    private JobRun createRun(JobMessage message, Instant now) {
        JobRun run = new JobRun();
        run.setJobId(message.jobId());
        run.setAttemptNumber(message.attemptNumber());
        run.setStatus(JobStatus.RUNNING);
        run.setStartTime(now);
        run.setModifiedTime(now);
        run.setExecutorId(executorId);
        try {
            return jobRunRepository.save(run);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.info("Job {} attempt {} concurrently claimed elsewhere, skipping",
                    message.jobId(), message.attemptNumber());
            return null;
        }
    }

    private void finish(Long runId, String jobId, JobStatus status, String errorMsg) {
        jobRunRepository.finishRun(runId, status, Instant.now(), errorMsg);
        jobRepository.updateStatus(jobId, status, Instant.now());
    }

    /** 瞬时错误 -> jobs.retry（attempt+1）；重试耗尽 -> jobs.dead（DLQ） */
    private void routeAfterFailure(JobMessage message, Exception cause) {
        int maxRetries = message.maxRetries();
        if (message.attemptNumber() < maxRetries) {
            if (retryBackoffMs > 0) {
                try {
                    Thread.sleep(retryBackoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
            JobMessage next = new JobMessage(
                    message.jobId(), message.name(), message.payload(),
                    message.attemptNumber() + 1, maxRetries, message.scheduledTime(), UUID.randomUUID().toString());
            kafkaTemplate.send(KafkaTopics.RETRY, message.jobId(), JsonCodec.write(next));
            log.warn("Job {} attempt {} failed, re-queued to {} (next attempt {}): {}",
                    message.jobId(), message.attemptNumber(), KafkaTopics.RETRY,
                    message.attemptNumber() + 1, cause.getMessage());
        } else {
            kafkaTemplate.send(KafkaTopics.DEAD, message.jobId(), JsonCodec.write(message));
            log.error("Job {} exhausted all {} retries, sent to DLQ {}: {}",
                    message.jobId(), maxRetries, KafkaTopics.DEAD, cause.getMessage());
        }
    }
}
