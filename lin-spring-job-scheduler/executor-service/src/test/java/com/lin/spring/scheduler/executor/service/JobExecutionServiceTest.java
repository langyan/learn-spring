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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JobExecutionServiceTest {

    @Mock
    private JobRunRepository jobRunRepository;
    @Mock
    private JobRepository jobRepository;
    @Mock
    private HeartbeatService heartbeatService;
    @Mock
    private JobHandler jobHandler;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private KVStore kvStore;

    private JobExecutionService service;

    private final JobMessage message = new JobMessage(
            "job-1", "demo", "{\"sleepMs\":1}", 0, 2, Instant.now(), "trace-1");

    @BeforeEach
    void setUp() {
        service = new JobExecutionService(jobRunRepository, jobRepository, heartbeatService,
                jobHandler, kafkaTemplate, kvStore);
        ReflectionTestUtils.setField(service, "retryBackoffMs", 0);

        JobRun run = new JobRun();
        run.setId(42L);
        run.setJobId("job-1");
        run.setAttemptNumber(0);
        run.setStatus(JobStatus.RUNNING);
        lenient().when(jobRunRepository.save(any(JobRun.class))).thenReturn(run);
        lenient().when(heartbeatService.start(any())).thenReturn(() -> {
        });
        lenient().when(kvStore.hasKey(anyString())).thenReturn(false);
        lenient().when(jobRunRepository.findByJobIdAndAttemptNumber(anyString(), anyInt()))
                .thenReturn(Optional.empty());
    }

    @Test
    void preFlightCancelCheckHaltsExecution() throws Exception {
        when(kvStore.hasKey("JOB_job-1_CANCEL")).thenReturn(true);

        service.execute(message);

        verify(jobRepository).updateStatus(eq("job-1"), eq(JobStatus.CANCELLED), any(Instant.class));
        verify(jobHandler, never()).execute(any());
    }

    @Test
    void completedJobWritesBackSuccess() throws Exception {
        service.execute(message);

        verify(jobRepository).updateStatus(eq("job-1"), eq(JobStatus.RUNNING), any(Instant.class));
        verify(jobRunRepository).finishRun(eq(42L), eq(JobStatus.COMPLETED), any(Instant.class), isNull());
        verify(jobRepository).updateStatus(eq("job-1"), eq(JobStatus.COMPLETED), any(Instant.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void transientFailureRoutesToRetryTopicWithNextAttempt() throws Exception {
        doThrow(new IllegalStateException("boom")).when(jobHandler).execute(any());

        service.execute(message);

        verify(jobRunRepository).finishRun(eq(42L), eq(JobStatus.FAILED), any(Instant.class), eq("boom"));
        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq(KafkaTopics.RETRY), eq("job-1"), captor.capture());
        JobMessage retried = JsonCodec.read(captor.getValue(), JobMessage.class);
        assertThat(retried.attemptNumber()).isEqualTo(1);
    }

    @Test
    void exhaustedRetriesRouteToDeadLetter() throws Exception {
        JobMessage lastAttempt = new JobMessage("job-1", "demo", "{}", 2, 2, Instant.now(), "trace");
        doThrow(new IllegalStateException("fatal")).when(jobHandler).execute(any());

        service.execute(lastAttempt);

        verify(kafkaTemplate).send(eq(KafkaTopics.DEAD), eq("job-1"), anyString());
    }

    @Test
    void duplicateDeliveryIsSkipped() throws Exception {
        when(jobRunRepository.findByJobIdAndAttemptNumber("job-1", 0))
                .thenReturn(Optional.of(new JobRun()));

        service.execute(message);

        verify(jobHandler, never()).execute(any());
    }
}
