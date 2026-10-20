package com.lin.spring.webhook.publisher;

import com.lin.spring.webhook.dto.WebhookEvent;
import com.lin.spring.webhook.service.EventDispatcher;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 进程内事件总线，供 local profile 免 Docker 演示使用。
 */
@Component
@Profile("local")
@RequiredArgsConstructor
public class InProcessEventBus implements EventBus {

	private final EventDispatcher dispatcher;

	@Override
	public void publish(WebhookEvent event) {
		dispatcher.dispatch(event);
	}
}
