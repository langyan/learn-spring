package com.lin.spring.scheduler.watcher.publisher;

import com.lin.spring.scheduler.common.entity.Job;
import com.lin.spring.scheduler.common.messaging.JobMessage;
import com.lin.spring.scheduler.common.messaging.KafkaTopics;
import com.lin.spring.scheduler.common.util.JsonCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Kafka 派发封装：批量同步等待 broker 确认（allOf + 超时）。
 * 发送失败抛出异常 -> 调用方事务回滚 -> 状态回退 PENDING，下个轮询周期重试，保证至少一次派发。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${scheduler.publish-timeout-seconds:60}")
    private int publishTimeoutSeconds;

    public void publishBatch(List<Job> jobs) {
        List<CompletableFuture<?>> futures = new ArrayList<>(jobs.size());
        for (Job job : jobs) {
            futures.add(kafkaTemplate.send(KafkaTopics.RUN, job.getJobId(),
                    JsonCodec.write(toMessage(job, 0))));
        }
        awaitAll(futures, jobs.size());
    }

    public void publishRetry(Job job, int nextAttempt) {
        publishOne(KafkaTopics.RETRY, job, nextAttempt);
    }

    public void publishDead(Job job, int attempt) {
        publishOne(KafkaTopics.DEAD, job, attempt);
    }

    private void publishOne(String topic, Job job, int attempt) {
        try {
            kafkaTemplate.send(topic, job.getJobId(), JsonCodec.write(toMessage(job, attempt)))
                    .get(publishTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka publish failed to " + topic + " for job " + job.getJobId(), e);
        }
    }

    private void awaitAll(List<CompletableFuture<?>> futures, int total) {
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(publishTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Kafka batch publish failed (" + total + " jobs)", e);
        }
    }

    private JobMessage toMessage(Job job, int attempt) {
        return new JobMessage(
                job.getJobId(), job.getName(), job.getPayload(),
                attempt, job.getRetries(), job.getScheduledTime(), UUID.randomUUID().toString());
    }
}
