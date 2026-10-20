package com.lin.spring.scheduler.watcher.dispatch;

import com.lin.spring.scheduler.common.domain.JobStatus;
import com.lin.spring.scheduler.common.entity.Job;
import com.lin.spring.scheduler.watcher.publisher.JobPublisher;
import com.lin.spring.scheduler.watcher.repository.JobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DispatchServiceTest {

    @Mock
    private JobRepository jobRepository;
    @Mock
    private JobPublisher publisher;

    private DispatchService dispatchService;

    @BeforeEach
    void setUp() {
        dispatchService = new DispatchService(jobRepository, publisher);
        ReflectionTestUtils.setField(dispatchService, "skipLocked", false);
    }

    private Job queuedJob(String id) {
        Job job = new Job();
        job.setJobId(id);
        job.setStatus(JobStatus.QUEUED);
        job.setName("demo");
        job.setPayload("{}");
        job.setRetries(3);
        job.setScheduledTime(Instant.now());
        return job;
    }

    @Test
    void dispatchesDueJobsWithinWindow() {
        Instant from = Instant.now().minusSeconds(20);
        Instant to = Instant.now().plusSeconds(5);

        when(jobRepository.findJobIdByStatusAndScheduledTimeBetweenOrderByScheduledTime(
                any(), any(), any(), any())).thenReturn(List.of("job-1", "job-2"));
        when(jobRepository.markQueued(anyList(), any())).thenReturn(2);
        when(jobRepository.findAllById(List.of("job-1", "job-2")))
                .thenReturn(List.of(queuedJob("job-1"), queuedJob("job-2")));

        int dispatched = dispatchService.dispatch(from, to, 100);

        assertThat(dispatched).isEqualTo(2);
        verify(jobRepository).markQueued(anyList(), any());
        verify(publisher).publishBatch(anyList());
    }

    @Test
    void publishesOnlyActuallyClaimedJobs() {
        when(jobRepository.findJobIdByStatusAndScheduledTimeBetweenOrderByScheduledTime(
                any(), any(), any(), any())).thenReturn(List.of("job-1", "job-2"));
        // job-2 已被并发实例抢先置为 QUEUED（markQueued 只更新到 1 行）
        when(jobRepository.markQueued(anyList(), any())).thenReturn(1);
        when(jobRepository.findAllById(anyList())).thenReturn(List.of(queuedJob("job-1")));

        int dispatched = dispatchService.dispatch(Instant.now(), Instant.now(), 100);

        assertThat(dispatched).isEqualTo(1);
        verify(publisher).publishBatch(anyList());
    }

    @Test
    void skipsPublishWhenNothingDue() {
        when(jobRepository.findJobIdByStatusAndScheduledTimeBetweenOrderByScheduledTime(
                any(), any(), any(), any())).thenReturn(List.of());

        int dispatched = dispatchService.dispatch(Instant.now(), Instant.now(), 100);

        assertThat(dispatched).isZero();
        verify(publisher, never()).publishBatch(anyList());
        verify(jobRepository, never()).markQueued(anyList(), any());
    }

    @Test
    void usesSkipLockedQueryOnMysqlProfile() {
        ReflectionTestUtils.setField(dispatchService, "skipLocked", true);
        when(jobRepository.lockDueJobIds(any(), any(), anyInt())).thenReturn(List.of());
        when(jobRepository.findJobIdByStatusAndScheduledTimeBetweenOrderByScheduledTime(
                any(), any(), any(), any())).thenReturn(List.of());

        dispatchService.dispatch(Instant.now(), Instant.now(), 100);

        verify(jobRepository).lockDueJobIds(any(), any(), anyInt());
    }

    @Test
    void usesPageableLimitForDevQuery() {
        ReflectionTestUtils.setField(dispatchService, "skipLocked", false);
        when(jobRepository.findJobIdByStatusAndScheduledTimeBetweenOrderByScheduledTime(
                any(), any(), any(), any())).thenReturn(List.of());

        dispatchService.dispatch(Instant.now(), Instant.now(), 500);

        verify(jobRepository).findJobIdByStatusAndScheduledTimeBetweenOrderByScheduledTime(
                any(), any(), any(), org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 500)));
    }
}
