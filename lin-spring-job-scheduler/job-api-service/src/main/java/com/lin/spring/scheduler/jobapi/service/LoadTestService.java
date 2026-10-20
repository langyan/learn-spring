package com.lin.spring.scheduler.jobapi.service;

import com.lin.spring.scheduler.common.domain.JobStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 压测工具：批量并发提交作业（模拟 10k+/s 摄入），并输出吞吐与延迟分位统计。
 *
 * - 提交：固定线程池并行 JDBC batch INSERT（配合 MySQL rewriteBatchedStatements=true），
 *   作业的 scheduled_time 随机分布在 [now, now+spreadMs) 窗口内。
 * - 统计：对 meta='loadtest' 的作业，按 job_run(scheduled_time -> start_time) 计算端到端
 *   调度延迟（目标 ≤ 2s），按完成窗口计算吞吐（目标 10k+/s）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoadTestService {

    public static final String META = "loadtest";

    private final JdbcTemplate jdbcTemplate;

    @Value("${loadtest.insert-threads:8}")
    private int insertThreads;

    @Value("${loadtest.insert-batch-size:500}")
    private int batchSize;

    public record SubmitResult(int submitted, long elapsedMs, double submitRatePerSec) {
    }

    public record Stats(Map<String, Long> statusCounts, long completed, long failed, long cancelled,
                        double throughputPerSec, long latencyP50Ms, long latencyP95Ms, long latencyP99Ms,
                        Double avgLatencyMs, double completionWindowSeconds) {
    }

    public SubmitResult submit(int count, long spreadMs, int sleepMs) {
        Instant base = Instant.now();
        Random random = new Random();
        ExecutorService pool = Executors.newFixedThreadPool(insertThreads);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            int chunk = (count + insertThreads - 1) / insertThreads;
            for (int t = 0; t < insertThreads; t++) {
                int from = t * chunk;
                int to = Math.min(count, from + chunk);
                if (from >= to) {
                    break;
                }
                Instant created = base;
                futures.add(pool.submit(() -> insertRange(from, to, spreadMs, sleepMs, created, random)));
            }
            int inserted = 0;
            for (Future<Integer> f : futures) {
                inserted += f.get();
            }
            long elapsed = Duration.between(base, Instant.now()).toMillis();
            double rate = elapsed == 0 ? inserted : inserted * 1000.0 / elapsed;
            log.info("Loadtest: submitted {} jobs in {} ms ({} jobs/s)", inserted, elapsed, String.format("%.0f", rate));
            return new SubmitResult(inserted, elapsed, rate);
        } catch (Exception e) {
            throw new IllegalStateException("Loadtest submit failed: " + e.getMessage(), e);
        } finally {
            pool.shutdown();
        }
    }

    private int insertRange(int from, int to, long spreadMs, int sleepMs, Instant created, Random random) {
        String sql = """
                INSERT INTO job (job_id, status, name, scheduled_time, cron_expression, payload, retries, meta, created_time, modified_time)
                VALUES (?, 'PENDING', ?, ?, NULL, ?, 3, ?, ?, ?)
                """;
        int done = 0;
        for (int start = from; start < to; start += batchSize) {
            List<Object[]> rows = new ArrayList<>();
            int end = Math.min(to, start + batchSize);
            for (int i = start; i < end; i++) {
                Instant scheduled = created.plusMillis(random.nextInt((int) Math.max(spreadMs, 1)));
                rows.add(new Object[]{
                        UUID.randomUUID().toString(),
                        "loadtest-job-" + i,
                        Timestamp.from(scheduled),
                        "{\"sleepMs\":" + sleepMs + "}",
                        META,
                        Timestamp.from(created),
                        Timestamp.from(created)
                });
            }
            jdbcTemplate.batchUpdate(sql, rows);
            done += rows.size();
        }
        return done;
    }

    public Stats stats() {
        Map<String, Long> statusCounts = jdbcTemplate.query(
                        "select status, count(*) cnt from job where meta = ? group by status",
                        (rs, i) -> Map.entry(rs.getString(1), rs.getLong(2))).stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        record CompletedRow(Instant scheduled, Instant startTime, Instant endTime) {
        }

        List<CompletedRow> completedRows = new ArrayList<>(jdbcTemplate.query(
                "select j.scheduled_time, r.start_time, r.end_time from job j join job_run r on r.job_id = j.job_id "
                        + "where j.meta = ? and r.status = ?",
                (rs, i) -> new CompletedRow(
                        rs.getTimestamp(1).toInstant(),
                        rs.getTimestamp(2).toInstant(),
                        rs.getTimestamp(3) == null ? rs.getTimestamp(2).toInstant() : rs.getTimestamp(3).toInstant()),
                META, JobStatus.COMPLETED.name()));

        long completed = statusCounts.getOrDefault(JobStatus.COMPLETED.name(), 0L);
        long failed = statusCounts.getOrDefault(JobStatus.FAILED.name(), 0L);
        long cancelled = statusCounts.getOrDefault(JobStatus.CANCELLED.name(), 0L);

        double windowSeconds = completedRows.isEmpty() ? 0
                : Duration.between(
                completedRows.stream().map(CompletedRow::startTime).min(Instant::compareTo).orElseThrow(),
                completedRows.stream().map(CompletedRow::endTime).max(Instant::compareTo).orElseThrow())
                .toMillis() / 1000.0;
        double throughput = windowSeconds <= 0 ? 0 : completed / windowSeconds;

        List<Long> sorted = completedRows.stream()
                .map(r -> Duration.between(r.scheduled(), r.startTime()).toMillis())
                .sorted()
                .toList();
        long p50 = percentile(sorted, 0.50);
        long p95 = percentile(sorted, 0.95);
        long p99 = percentile(sorted, 0.99);
        double avg = sorted.isEmpty() ? 0 : sorted.stream().mapToLong(Long::longValue).average().orElse(0);

        return new Stats(statusCounts, completed, failed, cancelled,
                throughput, p50, p95, p99, avg, windowSeconds);
    }

    private long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int idx = (int) Math.ceil(p * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(idx, sorted.size() - 1)));
    }

    /** 清空历史压测数据（作业定义 + 执行日志） */
    @Transactional
    public int reset() {
        jdbcTemplate.update("delete from job_run where job_id in (select job_id from job where meta = ?)", META);
        return jdbcTemplate.update("delete from job where meta = ?", META);
    }
}
