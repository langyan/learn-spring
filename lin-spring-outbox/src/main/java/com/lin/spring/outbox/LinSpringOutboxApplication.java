package com.lin.spring.outbox;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Transactional Outbox 演示应用：订单与待投递事件在同一本地事务提交。
 */
@SpringBootApplication(exclude = KafkaAutoConfiguration.class)
@EnableScheduling
public class LinSpringOutboxApplication {

	public static void main(String[] args) {
		SpringApplication.run(LinSpringOutboxApplication.class, args);
	}
}
