package com.lin.spring.scheduler.watcher.scheduler;

import com.lin.spring.scheduler.common.store.KVStore;
import com.lin.spring.scheduler.watcher.dispatch.DispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

/**
 * 守望者轮询：
 * - 每 20s 拉取调度时间落在 [lastPollTime, now + pollAhead] 的 PENDING 作业；
 * - Last Poll Time 持久化在 Redis：服务重启/崩溃后能准确判断中断位置，不漏作业；
 * - 派发成功后才推进断点（派发失败作业回退 PENDING，断点未动，下轮自动补上）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PollingScheduler {

    public static final String LAST_POLL_KEY = "WATCHER_LAST_POLL_TIME";

    private final DispatchService dispatchService;
    private final KVStore kvStore;

    @Value("${scheduler.poll-ahead-ms:5000}")
    private long pollAheadMs;

    @Value("${scheduler.poll-batch-size:10000}")
    private int pollBatchSize;

    @Scheduled(fixedDelayString = "${scheduler.poll-interval-ms:20000}")
    public void poll() {
        Instant now = Instant.now();
        Instant windowEnd = now.plusMillis(pollAheadMs);
        // 无断点（首次启动）时从 EPOCH 开始：所有已到期的 PENDING 作业立即补派
        Instant windowStart = kvStore.get(LAST_POLL_KEY)
                .map(Instant::parse)
                .orElse(Instant.EPOCH);

        if (!windowEnd.isAfter(windowStart)) {
            return;
        }
        try {
            int dispatched = dispatchService.dispatch(windowStart, windowEnd, pollBatchSize);
            kvStore.put(LAST_POLL_KEY, windowEnd.toString(), Duration.ofDays(7));
            log.debug("Poll done: window [{}, {}], dispatched {}", windowStart, windowEnd, dispatched);
        } catch (Exception e) {
            log.error("Poll failed, will retry next cycle: {}", e.getMessage(), e);
        }
    }
}
