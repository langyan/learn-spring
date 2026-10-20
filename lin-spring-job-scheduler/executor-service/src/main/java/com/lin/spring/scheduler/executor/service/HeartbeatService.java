package com.lin.spring.scheduler.executor.service;

import com.lin.spring.scheduler.executor.repository.JobRunRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 执行期心跳：执行器处理繁重任务期间，每 10 秒主动更新 JobRun.modified_time。
 * 若执行节点崩溃，心跳停止 -> Watcher 僵尸侦测（20s 阈值）发现并重触发。
 */
@Slf4j
@Service
public class HeartbeatService {

    private final JobRunRepository jobRunRepository;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, "job-heartbeat");
                t.setDaemon(true);
                return t;
            });

    @Value("${scheduler.heartbeat-interval-ms:10000}")
    private long heartbeatIntervalMs;

    public HeartbeatService(JobRunRepository jobRunRepository) {
        this.jobRunRepository = jobRunRepository;
    }

    /** 启动指定执行记录的心跳，返回句柄（try-with-resources 自动停止） */
    public AutoCloseable start(Long jobRunId) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                jobRunRepository.touchHeartbeat(jobRunId, java.time.Instant.now());
            } catch (Exception e) {
                log.warn("Heartbeat failed for run {}: {}", jobRunId, e.getMessage());
            }
        }, heartbeatIntervalMs, heartbeatIntervalMs, TimeUnit.MILLISECONDS);
        return () -> future.cancel(false);
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }
}
