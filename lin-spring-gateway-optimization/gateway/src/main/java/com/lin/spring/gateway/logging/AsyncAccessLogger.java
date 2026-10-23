package com.lin.spring.gateway.logging;

import com.lin.spring.gateway.config.GatewayOptimizationProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 异步访问日志器 —— "同步日志是隐藏的性能杀手"的修复。
 *
 * 请求路径：offer 进内存队列（非阻塞、微秒级）；队列满时直接丢弃并计数
 * （负载切断：日志永不反压请求处理）。
 *
 * 后台路径：单 worker 线程批量取走事件，一次刷出一批
 * （演示为 SLF4J 单行输出；生产对接 Kafka/集中式日志）。
 *
 * 指标：gateway.log.queue.size（gauge）、gateway.log.dropped（counter）、
 * gateway.log.batch.size（summary）。
 */
@Slf4j
@Component
public class AsyncAccessLogger {

    /** 独立 logger 名，便于在生产中路由到异步 appender / 采集器 */
    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("GATEWAY_ACCESS_LOG");

    private final GatewayOptimizationProperties.AccessLog config;
    private final BlockingQueue<AccessLogEvent> queue;
    private final Counter dropped;
    private final DistributionSummary batchSize;
    private volatile boolean running = true;
    private Thread worker;

    public AsyncAccessLogger(GatewayOptimizationProperties properties, MeterRegistry meterRegistry) {
        this.config = properties.getAccessLog();
        this.queue = new ArrayBlockingQueue<>(config.getQueueCapacity());
        this.dropped = Counter.builder("gateway.log.dropped")
                .description("Access log events dropped because the in-memory buffer was full (load shedding)")
                .register(meterRegistry);
        this.batchSize = DistributionSummary.builder("gateway.log.batch.size")
                .description("Access log events flushed per background batch")
                .register(meterRegistry);
        meterRegistry.gauge("gateway.log.queue.size", queue, BlockingQueue::size);
    }

    /** 请求路径调用：非阻塞入队，绝不产生 I/O */
    public void offer(AccessLogEvent event) {
        if (!queue.offer(event)) {
            dropped.increment(); // 削峰：宁可丢日志，不能堵请求
        }
    }

    @PostConstruct
    void start() {
        worker = new Thread(this::drainLoop, "async-access-log-worker");
        worker.setDaemon(true);
        worker.start();
        log.info("Async access logger started: capacity={}, batchSize={}, flushInterval={}ms",
                config.getQueueCapacity(), config.getBatchSize(), config.getFlushIntervalMs());
    }

    private void drainLoop() {
        List<AccessLogEvent> batch = new ArrayList<>(config.getBatchSize());
        while (running || !queue.isEmpty()) {
            try {
                // 阻塞等首条（限时），随后一次性取走当前积压（≤batchSize）
                AccessLogEvent first = queue.poll(config.getFlushIntervalMs(), TimeUnit.MILLISECONDS);
                if (first == null) {
                    continue;
                }
                batch.add(first);
                queue.drainTo(batch, config.getBatchSize() - 1);

                batchSize.record(batch.size());
                flush(batch);
                batch.clear();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception ex) {
                // 后台刷盘失败不影响请求路径；清空本批继续
                log.warn("Access log flush failed: {}", ex.getMessage());
                batch.clear();
            }
        }
    }

    private void flush(List<AccessLogEvent> batch) {
        // 模拟批量发送到集中式日志系统：一批一条日志
        StringBuilder sb = new StringBuilder("ACCESS_BATCH events=").append(batch.size()).append(" :: ");
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) {
                sb.append(" || ");
            }
            sb.append(batch.get(i).toLogLine());
        }
        ACCESS_LOG.info(sb.toString());
    }

    @PreDestroy
    void shutdown() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
        // 最后尽力冲刷剩余事件
        List<AccessLogEvent> remaining = new ArrayList<>(config.getBatchSize());
        queue.drainTo(remaining);
        if (!remaining.isEmpty()) {
            flush(remaining);
        }
    }
}
