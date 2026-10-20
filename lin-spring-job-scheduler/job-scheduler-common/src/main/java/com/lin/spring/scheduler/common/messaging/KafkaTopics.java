package com.lin.spring.scheduler.common.messaging;

/**
 * Kafka 主题常量：
 * - jobs.run  : 标准初次执行（Watcher 派发 / runNow 快车道直推）
 * - jobs.retry: 瞬时错误重试 / 僵尸作业重新触发
 * - jobs.dead : 重试耗尽的死信队列（DLQ）
 */
public final class KafkaTopics {

    public static final String RUN = "jobs.run";
    public static final String RETRY = "jobs.retry";
    public static final String DEAD = "jobs.dead";

    public static final String[] ALL = {RUN, RETRY, DEAD};

    private KafkaTopics() {
    }
}
