package com.lin.spring.scheduler.executor;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;
import com.lin.spring.scheduler.common.entity.JobRun;
import com.lin.spring.scheduler.common.messaging.JobMessage;
import com.lin.spring.scheduler.common.messaging.KafkaTopics;
import com.lin.spring.scheduler.common.util.JsonCodec;
import com.lin.spring.scheduler.executor.repository.JobRepository;
import com.lin.spring.scheduler.executor.repository.JobRunRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * dev profile 端到端集成测试：H2 内存库 + 嵌入式 Kafka（KRaft，本机 9092）。
 * 验证完整链路：KafkaTemplate 发送 JobMessage -> @KafkaListener 消费 -> 取消预检 ->
 * 幂等创建 JobRun -> MockJobHandler 执行 -> 状态回写 COMPLETED。
 */
@SpringBootTest
@ActiveProfiles("dev")
class ExecutorEndToEndDevTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;
    @Autowired
    private JobRunRepository jobRunRepository;
    @Autowired
    private JobRepository jobRepository;

    @Test
    void jobMessageIsConsumedExecutedAndWrittenBack() {
        String jobId = "e2e-" + System.nanoTime();
        Job job = new Job();
        job.setJobId(jobId);
        job.setStatus(JobStatus.QUEUED);
        job.setName("e2e-demo");
        job.setScheduledTime(Instant.now());
        job.setPayload("{\"sleepMs\":5}");
        job.setRetries(3);
        job.setCreatedTime(Instant.now());
        job.setModifiedTime(Instant.now());
        jobRepository.save(job);

        JobMessage message = new JobMessage(jobId, "e2e-demo", "{\"sleepMs\":5}", 0, 3, Instant.now(), "trace-e2e");
        kafkaTemplate.send(KafkaTopics.RUN, jobId, JsonCodec.write(message));

        Awaitility.await()
                .atMost(Duration.ofSeconds(30))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() -> {
                    JobRun run = jobRunRepository.findByJobIdAndAttemptNumber(jobId, 0)
                            .orElseThrow(() -> new AssertionError("JobRun not created yet"));
                    assertThat(run.getStatus()).isEqualTo(JobStatus.COMPLETED);
                    assertThat(run.getEndTime()).isNotNull();
                });

        Job completed = jobRepository.findById(jobId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(JobStatus.COMPLETED);
    }
}
