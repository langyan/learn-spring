package com.lin.spring.websocket;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LinSpringWebsocketApplication {

	public static void main(String[] args) {
		SpringApplication.run(LinSpringWebsocketApplication.class, args);
	}

}
