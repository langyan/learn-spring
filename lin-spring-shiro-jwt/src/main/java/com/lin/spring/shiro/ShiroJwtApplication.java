package com.lin.spring.shiro;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class ShiroJwtApplication {

	public static void main(String[] args) {
		SpringApplication.run(ShiroJwtApplication.class, args);
	}
}
