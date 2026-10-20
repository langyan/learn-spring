package com.lin.spring.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 千万级通知系统演示应用入口。
 * <p>
 * 默认模式：进程内异步投递（无需 Kafka/Redis）。<br>
 * Kafka 模式：{@code -Dspring-boot.run.profiles=kafka}，需先 {@code docker compose up -d}。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
public class LinSpringNotificationApplication {

	/**
	 * 启动 Spring Boot 应用。
	 *
	 * @param args 命令行参数
	 */
	public static void main(String[] args) {
		SpringApplication.run(LinSpringNotificationApplication.class, args);
	}
}
