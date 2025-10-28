package com.lin.spring.bulkhead;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@EnableAspectJAutoProxy
@SpringBootApplication
public class LinSpringResilience4jBulkheadApplication {

	public static void main(String[] args) {
		SpringApplication.run(LinSpringResilience4jBulkheadApplication.class, args);
	}

}
