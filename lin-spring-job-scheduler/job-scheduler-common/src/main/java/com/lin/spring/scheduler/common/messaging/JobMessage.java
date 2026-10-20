package com.lin.spring.scheduler.common.messaging;

import java.time.Instant;

/**
 * Kafka 消息契约：三个服务（job-api / watcher / executor）共同依赖的精确定义。
 * 经 JsonCodec 序列化为 JSON 字符串在 Kafka 上传输（key 为 jobId，保证同作业顺序）。
 */
public record JobMessage(
        String jobId,
        String name,
        String payload,
        int attemptNumber,
        int maxRetries,
        Instant scheduledTime,
        String traceId
) {
}
