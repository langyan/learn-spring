package com.lin.spring.bean;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class LinSpringBeanLifecycleApplication {

	public static void main(String[] args) {
		SpringApplication.run(LinSpringBeanLifecycleApplication.class, args);
	}

}
