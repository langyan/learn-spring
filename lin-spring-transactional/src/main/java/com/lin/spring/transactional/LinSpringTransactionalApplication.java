package com.lin.spring.transactional;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories
public class LinSpringTransactionalApplication {

	public static void main(String[] args) {
		SpringApplication.run(LinSpringTransactionalApplication.class, args);
	}

}
