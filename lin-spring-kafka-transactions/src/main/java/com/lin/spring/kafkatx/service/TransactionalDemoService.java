package com.lin.spring.kafkatx.service;

import com.lin.spring.kafkatx.config.KafkaTxTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * 使用 {@link KafkaTemplate#executeInTransaction} 演示 Kafka 生产者事务：
 * 向两个 Topic 发送要么一起提交，要么在异常路径上全部中止。
 */
@Service
@RequiredArgsConstructor
public class TransactionalDemoService {

	private final KafkaTemplate<String, String> kafkaTemplate;

	/**
	 * 在同一 Kafka 事务内向 {@link KafkaTxTopics#DEMO_TX_A} 与 {@link KafkaTxTopics#DEMO_TX_B} 各发送一条消息并提交。
	 * <p>消息 value 格式为 {@code correlationId|a} 与 {@code correlationId|b}，便于测试断言。</p>
	 *
	 * @param correlationId 关联 ID，作为 key 与 value 前缀
	 */
	public void sendCommittedPair(String correlationId) {
		kafkaTemplate.executeInTransaction(kt -> {
			kt.send(KafkaTxTopics.DEMO_TX_A, correlationId, correlationId + "|a");
			kt.send(KafkaTxTopics.DEMO_TX_B, correlationId, correlationId + "|b");
			return true;
		});
	}

	/**
	 * 在同一 Kafka 事务内发送两条消息后主动失败，触发事务中止；{@code read_committed} 消费者不应看到任一条。
	 *
	 * @param correlationId 关联 ID
	 * @throws IllegalStateException 固定抛出，用于模拟业务失败
	 */
	public void sendFailingPair(String correlationId) {
		kafkaTemplate.executeInTransaction(kt -> {
			kt.send(KafkaTxTopics.DEMO_TX_A, correlationId, correlationId + "|a");
			kt.send(KafkaTxTopics.DEMO_TX_B, correlationId, correlationId + "|b");
			throw new IllegalStateException("simulated failure after sends");
		});
	}
}
