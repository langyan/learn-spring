package com.lin.spring.webhook.publisher;

import com.lin.spring.webhook.dto.WebhookEvent;

/**
 * 事件总线：默认走 Kafka，local profile 下走进程内直投。
 */
public interface EventBus {

	void publish(WebhookEvent event);
}
