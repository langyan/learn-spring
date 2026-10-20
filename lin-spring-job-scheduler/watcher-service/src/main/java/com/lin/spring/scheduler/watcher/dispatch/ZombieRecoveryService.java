package com.lin.spring.scheduler.watcher.dispatch;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;
import com.lin.spring.scheduler.common.entity.JobRun;
import com.lin.spring.scheduler.watcher.publisher.JobPublisher;
import com.lin.spring.scheduler.watcher.repository.JobRepository;
import com.lin.spring.scheduler.watcher.repository.JobRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 僵尸回收（事务内）：
 * 1) 关闭心跳超时的 RUNNING 执行记录（FAILED，错误信息标记 zombie）；
 * 2) 作业标记 FAILED；
 * 3) 剩余重试次数未耗尽 -> 推 jobs.retry（attempt+1）；耗尽 -> 推 jobs.dead（DLQ）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ZombieRecoveryService {

    private static final String ZOMBIE_MSG = "zombie detected: executor heartbeat stale";

    private final JobRunRepository jobRunRepository;
    private final JobRepository jobRepository;
    private final JobPublisher publisher;

    @Transactional
    public int recoverStale(Instant heartbeatThreshold) {
        List<JobRun> staleRuns = jobRunRepository.findByStatusAndModifiedTimeBefore(JobStatus.RUNNING, heartbeatThreshold);
        if (staleRuns.isEmpty()) {
            return 0;
        }
        for (JobRun run : staleRuns) {
            Instant now = Instant.now();
            jobRunRepository.closeStaleRun(run.getId(), JobStatus.FAILED, now, ZOMBIE_MSG);

            Job job = jobRepository.findById(run.getJobId()).orElse(null);
            if (job == null) {
                continue;
            }
            job.setStatus(JobStatus.FAILED);
            job.setModifiedTime(now);
            jobRepository.save(job);

            int failedAttempt = run.getAttemptNumber();
            if (failedAttempt < job.getRetries()) {
                publisher.publishRetry(job, failedAttempt + 1);
                log.warn("Zombie job {} attempt {} re-triggered, next attempt {}", job.getJobId(), failedAttempt, failedAttempt + 1);
            } else {
                publisher.publishDead(job, failedAttempt);
                log.error("Zombie job {} exhausted all retries ({}), sent to DLQ jobs.dead", job.getJobId(), failedAttempt);
            }
        }
        return staleRuns.size();
    }
}
