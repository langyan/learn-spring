package com.lin.spring.kafkatx.listener;

import com.lin.spring.kafkatx.config.KafkaTxTopics;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 订阅演示 Topic，将已提交消息写入阻塞队列，供集成测试或观测使用。
 */
@Slf4j
@Component
public class DemoEventsListener {

	@Getter
	private final BlockingQueue<ReceivedRecord> received = new LinkedBlockingQueue<>();

	/**
	 * 清空队列（测试之间隔离）。
	 */
	public void clear() {
		received.clear();
	}

	@KafkaListener(
		topics = {
			KafkaTxTopics.DEMO_TX_A,
			KafkaTxTopics.DEMO_TX_B,
			KafkaTxTopics.DEMO_TX_DB
		},
		containerFactory = "kafkaListenerContainerFactory"
	)
	public void onMessage(
		String value,
		@Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
		Acknowledgment ack
	) {
		log.debug("Received committed message topic={} value={}", topic, value);
		received.offer(new ReceivedRecord(topic, value));
		ack.acknowledge();
	}

	/**
	 * 监听到的单条记录。
	 *
	 * @param topic 分区所属 Topic
	 * @param value 消息体
	 */
	public record ReceivedRecord(String topic, String value) {
	}
}
