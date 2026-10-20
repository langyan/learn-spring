package com.lin.spring.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Webhook 投递系统演示应用。
 */
@EnableScheduling
@ConfigurationPropertiesScan
@SpringBootApplication
public class LinSpringWebhookApplication {

	public static void main(String[] args) {
		SpringApplication.run(LinSpringWebhookApplication.class, args);
	}
}
