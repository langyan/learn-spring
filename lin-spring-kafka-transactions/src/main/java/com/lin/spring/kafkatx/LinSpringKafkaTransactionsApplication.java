package com.lin.spring.kafkatx;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Kafka 事务性生产与 {@code read_committed} 消费验证示例应用入口。
 */
@SpringBootApplication
@EnableTransactionManagement
public class LinSpringKafkaTransactionsApplication {

	public static void main(String[] args) {
		SpringApplication.run(LinSpringKafkaTransactionsApplication.class, args);
	}
}
