package com.lin.spring.events;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 可靠事件驱动架构演示应用。
 * <p>
 * 排除 {@link KafkaAutoConfiguration}：与 lin-spring-outbox 一致，仅在 {@code kafka} profile 下
 * 由 KafkaConfig 显式装配生产者/消费者，避免无 Broker 时默认自动配置导致启动失败。
 * </p>
 */
@SpringBootApplication(exclude = KafkaAutoConfiguration.class)
@EnableScheduling
@ConfigurationPropertiesScan
public class ReliableEventsApplication {

	public static void main(String[] args) {
		org.springframework.boot.SpringApplication.run(ReliableEventsApplication.class, args);
	}
}
