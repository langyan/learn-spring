package com.lin.spring.scheduler.common.config;

import com.lin.spring.scheduler.common.messaging.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kafka 三主题定义。分区数默认 12，支撑 10k+/s 吞吐（消费端可通过多实例水平扩展）。
 * KafkaAdmin 在各服务启动时自动创建/校准主题（嵌入式 broker 亦会预建）。
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic jobsRunTopic(@Value("${scheduler.topics.partitions:12}") int partitions) {
        return new NewTopic(KafkaTopics.RUN, partitions, (short) 1);
    }

    @Bean
    public NewTopic jobsRetryTopic(@Value("${scheduler.topics.partitions:12}") int partitions) {
        return new NewTopic(KafkaTopics.RETRY, partitions, (short) 1);
    }

    @Bean
    public NewTopic jobsDeadTopic(@Value("${scheduler.topics.partitions:12}") int partitions) {
        return new NewTopic(KafkaTopics.DEAD, partitions, (short) 1);
    }
}
