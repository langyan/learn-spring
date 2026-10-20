package com.lin.spring.scheduler.watcher.scheduler;

import com.lin.spring.scheduler.watcher.dispatch.ZombieRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * 僵尸侦测：Watcher 兼任系统监控器。
 * 执行器处理期间每 10s 更新 JobRun.modified_time（心跳）；若某执行记录 RUNNING
 * 但心跳超过 20s 未更新，判定执行节点已死亡 -> 关闭记录并重触发（retry / dead）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ZombieDetector {

    private final ZombieRecoveryService zombieRecoveryService;

    @Value("${scheduler.zombie-stale-after-ms:20000}")
    private long staleAfterMs;

    @Scheduled(fixedDelayString = "${scheduler.zombie-check-interval-ms:20000}")
    public void detect() {
        try {
            int recovered = zombieRecoveryService.recoverStale(Instant.now().minusMillis(staleAfterMs));
            if (recovered > 0) {
                log.warn("Zombie detection recovered {} stale executions", recovered);
            }
        } catch (Exception e) {
            log.error("Zombie detection failed: {}", e.getMessage(), e);
        }
    }
}
