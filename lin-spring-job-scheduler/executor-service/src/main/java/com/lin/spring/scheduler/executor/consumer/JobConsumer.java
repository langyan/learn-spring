package com.lin.spring.scheduler.executor.consumer;

import com.lin.spring.scheduler.common.messaging.JobMessage;
import com.lin.spring.scheduler.common.messaging.KafkaTopics;
import com.lin.spring.scheduler.common.util.JsonCodec;
import com.lin.spring.scheduler.executor.service.JobExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * 作业消费者：从 Kafka jobs.run / jobs.retry 拉取消息（手动 ACK，至少一次语义）。
 * 消费失败不抛出（重试/DLQ 已在执行服务内部路由），避免消息无限重投。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JobConsumer {

    private final JobExecutionService executionService;

    @KafkaListener(
            topics = {KafkaTopics.RUN, KafkaTopics.RETRY},
            groupId = "${spring.kafka.consumer.group-id:job-executor}",
            concurrency = "${spring.kafka.listener.concurrency:6}")
    public void onJobMessage(String value, Acknowledgment ack) {
        try {
            JobMessage message = JsonCodec.read(value, JobMessage.class);
            executionService.execute(message);
        } catch (Exception e) {
            // 反序列化失败等毒消息：记录并跳过，不阻塞分区
            log.error("Failed to process kafka message: {} ({})", value, e.getMessage());
        } finally {
            ack.acknowledge();
        }
    }

    /** 死信队列观察者：重试耗尽的作业集中于此，供人工排查/告警接入 */
    @KafkaListener(topics = KafkaTopics.DEAD, groupId = "dead-letter-observer")
    public void onDeadLetter(String value) {
        log.error("DEAD LETTER received: {}", value);
    }
}
