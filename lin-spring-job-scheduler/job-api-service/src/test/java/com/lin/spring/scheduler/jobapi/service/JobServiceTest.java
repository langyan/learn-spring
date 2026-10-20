package com.lin.spring.scheduler.jobapi.service;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;
import com.lin.spring.scheduler.common.messaging.KafkaTopics;
import com.lin.spring.scheduler.common.store.KVStore;
import com.lin.spring.scheduler.jobapi.dto.CreateJobRequest;
import com.lin.spring.scheduler.jobapi.dto.JobResponse;
import com.lin.spring.scheduler.jobapi.dto.UpdateJobRequest;
import com.lin.spring.scheduler.jobapi.repository.JobRepository;
import com.lin.spring.scheduler.jobapi.repository.JobRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobRunRepository jobRunRepository;
    @Mock
    private KVStore kvStore;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, jobRunRepository, kvStore, kafkaTemplate);
        ReflectionTestUtils.setField(jobService, "cancelTtlSeconds", 30);
    }

    private Job pendingJob() {
        Job job = new Job();
        job.setJobId("job-1");
        job.setStatus(JobStatus.PENDING);
        job.setName("demo");
        job.setScheduledTime(Instant.now().plusSeconds(60));
        job.setPayload("{\"sleepMs\":5}");
        job.setRetries(3);
        job.setCreatedTime(Instant.now());
        job.setModifiedTime(Instant.now());
        return job;
    }

    @Test
    void createPersistsPendingJob() {
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));

        JobResponse response = jobService.create(new CreateJobRequest(
                "demo", Instant.parse("2026-10-19T08:00:00Z"), null, "{\"sleepMs\":5}", null, null));

        assertThat(response.status()).isEqualTo(JobStatus.PENDING);
        assertThat(response.retries()).isEqualTo(3);
    }

    @Test
    void cancelWritesRedisMarkerAndUpdatesStatus() {
        Job job = pendingJob();
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        jobService.cancel("job-1");

        // Redis TTL 标记：JOB_{id}_CANCEL，TTL 30s（覆盖已在 Kafka 队列但未执行的窗口）
        ArgumentCaptor<java.time.Duration> ttl = ArgumentCaptor.forClass(java.time.Duration.class);
        verify(kvStore).put(eq("JOB_job-1_CANCEL"), eq("1"), ttl.capture());
        assertThat(ttl.getValue().getSeconds()).isEqualTo(30);

        verify(jobRepository).updateStatusIfIn(
                eq("job-1"),
                eq(List.of(JobStatus.PENDING, JobStatus.QUEUED, JobStatus.RUNNING)),
                eq(JobStatus.CANCELLED),
                any(Instant.class));
    }

    @Test
    void cancelOnTerminalJobRejected() {
        Job job = pendingJob();
        job.setStatus(JobStatus.COMPLETED);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.cancel("job-1")).isInstanceOf(IllegalStateException.class);
        verify(kvStore, never()).put(anyString(), anyString(), any());
    }

    @Test
    void runNowFastLanesDirectlyToKafkaRunTopic() {
        Job job = pendingJob();
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        jobService.runNow("job-1");

        // 快车道：payload 直推 Kafka，绕过调度轮询
        verify(kafkaTemplate).send(eq(KafkaTopics.RUN), eq("job-1"), anyString());
        verify(jobRepository).updateStatusIfIn(
                eq("job-1"),
                eq(List.of(JobStatus.PENDING, JobStatus.QUEUED)),
                eq(JobStatus.QUEUED),
                any(Instant.class));
    }

    @Test
    void updateRejectedForNonPendingJob() {
        Job job = pendingJob();
        job.setStatus(JobStatus.QUEUED);
        when(jobRepository.findById("job-1")).thenReturn(Optional.of(job));

        assertThatThrownBy(() -> jobService.update("job-1", new UpdateJobRequest("x", null, null, null, null, null)))
                .isInstanceOf(IllegalStateException.class);
    }
}
