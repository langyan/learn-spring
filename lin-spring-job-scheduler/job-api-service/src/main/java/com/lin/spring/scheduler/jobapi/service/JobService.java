package com.lin.spring.scheduler.jobapi.service;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;
import com.lin.spring.scheduler.common.entity.JobRun;
import com.lin.spring.scheduler.common.messaging.JobMessage;
import com.lin.spring.scheduler.common.messaging.KafkaTopics;
import com.lin.spring.scheduler.common.store.KVStore;
import com.lin.spring.scheduler.common.util.JsonCodec;
import com.lin.spring.scheduler.jobapi.dto.CreateJobRequest;
import com.lin.spring.scheduler.jobapi.dto.JobResponse;
import com.lin.spring.scheduler.jobapi.dto.JobStatusResponse;
import com.lin.spring.scheduler.jobapi.dto.UpdateJobRequest;
import com.lin.spring.scheduler.jobapi.exception.JobNotFoundException;
import com.lin.spring.scheduler.jobapi.repository.JobRepository;
import com.lin.spring.scheduler.jobapi.repository.JobRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobService {

    private static final List<JobStatus> CANCELLABLE = List.of(JobStatus.PENDING, JobStatus.QUEUED, JobStatus.RUNNING);
    private static final List<JobStatus> RUNNABLE = List.of(JobStatus.PENDING, JobStatus.QUEUED);

    private final JobRepository jobRepository;
    private final JobRunRepository jobRunRepository;
    private final KVStore kvStore;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${scheduler.cancel-ttl-seconds:30}")
    private long cancelTtlSeconds;

    @Transactional
    public JobResponse create(CreateJobRequest request) {
        Instant now = Instant.now();
        Job job = new Job();
        job.setJobId(UUID.randomUUID().toString());
        job.setStatus(JobStatus.PENDING);
        job.setName(request.name());
        job.setScheduledTime(request.scheduledTime());
        job.setCronExpression(request.cronExpression());
        job.setPayload(request.payload());
        job.setRetries(request.retries() == null ? 3 : request.retries());
        job.setMeta(request.meta());
        job.setCreatedTime(now);
        job.setModifiedTime(now);
        return JobResponse.from(jobRepository.save(job));
    }

    @Transactional(readOnly = true)
    public JobResponse get(String jobId) {
        return JobResponse.from(findJob(jobId));
    }

    @Transactional(readOnly = true)
    public JobStatusResponse status(String jobId) {
        Job job = findJob(jobId);
        JobRun lastRun = jobRunRepository.findFirstByJobIdOrderByAttemptNumberDesc(jobId).orElse(null);
        return JobStatusResponse.of(job, lastRun);
    }

    /** 仅 PENDING 作业允许更新定义（QUEUED 之后的作业已在派发管道中，更新会破坏一致性） */
    @Transactional
    public JobResponse update(String jobId, UpdateJobRequest request) {
        Job job = findJob(jobId);
        if (job.getStatus() != JobStatus.PENDING) {
            throw new IllegalStateException("Only PENDING jobs can be updated, current: " + job.getStatus());
        }
        if (request.name() != null) job.setName(request.name());
        if (request.scheduledTime() != null) job.setScheduledTime(request.scheduledTime());
        if (request.cronExpression() != null) job.setCronExpression(request.cronExpression());
        if (request.payload() != null) job.setPayload(request.payload());
        if (request.retries() != null) job.setRetries(request.retries());
        if (request.meta() != null) job.setMeta(request.meta());
        job.setModifiedTime(Instant.now());
        return JobResponse.from(jobRepository.save(job));
    }

    /**
     * 取消机制（Redis TTL 技巧）：
     * 1) 无条件写 Redis 标记 JOB_{ID}_CANCEL（TTL 30s）——覆盖"已在 Kafka 队列但尚未执行"的窗口，
     *    Executor 发射前做 pre-flight 检查即可安全截停；
     * 2) 仅当作业仍处于可取消状态时更新 DB（已完成/已失败/已取消的不动）；
     * 3) 已在执行中的作业无法强停（RUNNING 的 DB 更新同样生效，但执行逻辑不会中断，
     *    至少一次语义下由 payload 幂等保证正确性）。
     */
    @Transactional
    public JobResponse cancel(String jobId) {
        Job job = findJob(jobId);
        if (job.getStatus().isTerminal()) {
            throw new IllegalStateException("Job already in terminal state: " + job.getStatus());
        }
        String cancelKey = "JOB_" + jobId + "_CANCEL";
        kvStore.put(cancelKey, "1", Duration.ofSeconds(cancelTtlSeconds));
        jobRepository.updateStatusIfIn(jobId, CANCELLABLE, JobStatus.CANCELLED, Instant.now());
        log.info("Job {} cancellation requested, Redis marker TTL {}s", jobId, cancelTtlSeconds);
        return JobResponse.from(jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId)));
    }

    /**
     * RUNNOW 快车道：绕过调度数据库，作业 payload 直接推送到 Kafka jobs.run，
     * 确保立即执行零额外延迟。DB 侧仅同步把状态置 QUEUED 以便监控可见。
     */
    @Transactional
    public JobResponse runNow(String jobId) {
        Job job = findJob(jobId);
        if (job.getStatus().isTerminal()) {
            throw new IllegalStateException("Job already in terminal state: " + job.getStatus());
        }
        JobMessage message = new JobMessage(
                job.getJobId(), job.getName(), job.getPayload(),
                0, job.getRetries(), job.getScheduledTime(), UUID.randomUUID().toString());
        kafkaTemplate.send(KafkaTopics.RUN, job.getJobId(), JsonCodec.write(message));
        jobRepository.updateStatusIfIn(jobId, RUNNABLE, JobStatus.QUEUED, Instant.now());
        log.info("Job {} fast-laned directly to Kafka topic {}", jobId, KafkaTopics.RUN);
        return JobResponse.from(jobRepository.findById(jobId).orElseThrow(() -> new JobNotFoundException(jobId)));
    }

    private Job findJob(String jobId) {
        Optional<Job> job = jobRepository.findById(jobId);
        return job.orElseThrow(() -> new JobNotFoundException(jobId));
    }
}
