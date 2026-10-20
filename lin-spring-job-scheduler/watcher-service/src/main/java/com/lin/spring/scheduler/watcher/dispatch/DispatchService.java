package com.lin.spring.scheduler.watcher.dispatch;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;
import com.lin.spring.scheduler.watcher.publisher.JobPublisher;
import com.lin.spring.scheduler.watcher.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 调度派发（事务内）：锁定 -> 批量置 QUEUED -> 推送 Kafka。
 *
 * 顺序保证一致性：先更新状态再发 Kafka；Kafka 批量确认失败则抛异常回滚，
 * 作业状态回退 PENDING，下个轮询周期重新派发（至少一次派发语义）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchService {

    private final JobRepository jobRepository;
    private final JobPublisher publisher;

    @Value("${scheduler.skip-locked:true}")
    private boolean skipLocked;

    @Transactional
    public int dispatch(Instant windowStart, Instant windowEnd, int limit) {
        List<String> dueIds = skipLocked
                ? jobRepository.lockDueJobIds(windowStart, windowEnd, limit)
                : jobRepository.findJobIdByStatusAndScheduledTimeBetweenOrderByScheduledTime(
                        JobStatus.PENDING, windowStart, windowEnd, PageRequest.of(0, limit));

        if (dueIds.isEmpty()) {
            return 0;
        }

        int claimed = jobRepository.markQueued(dueIds, Instant.now());
        if (claimed == 0) {
            return 0;
        }

        List<Job> toPublish = jobRepository.findAllById(dueIds).stream()
                .filter(j -> j.getStatus() == JobStatus.QUEUED)
                .toList();
        if (toPublish.isEmpty()) {
            return 0;
        }
        publisher.publishBatch(toPublish);
        log.info("Dispatched {} jobs in window [{}, {}]", toPublish.size(), windowStart, windowEnd);
        return toPublish.size();
    }
}
