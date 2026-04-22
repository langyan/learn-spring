package com.lin.spring.kafkatx.service;

import com.lin.spring.kafkatx.config.KafkaTxTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 使用 Spring 声明式事务：由 Boot 自动注册的 {@code kafkaTransactionManager}
 *（{@link org.springframework.kafka.transaction.KafkaTransactionManager}）绑定当前线程的生产者事务，
 *与 {@link TransactionalDemoService} 中的 {@link KafkaTemplate#executeInTransaction} 编程式写法对照。
 * <p>消息 value 后缀为 {@code |c}/{@code |d}，便于与编程式示例的 {@code |a}/{@code |b} 区分。</p>
 */
@Service
@RequiredArgsConstructor
public class TransactionalDeclarativeDemoService {

	/**
	 * Boot 在配置 {@code spring.kafka.producer.transaction-id-prefix} 时注册的 Bean 名称。
	 */
	public static final String KAFKA_TX_MANAGER_BEAN_NAME = "kafkaTransactionManager";

	private final KafkaTemplate<String, String> kafkaTemplate;

	/**
	 * 在 {@link Transactional} 边界内向双 Topic 发送并提交 Kafka 事务。
	 *
	 * @param correlationId 关联 ID
	 */
	@Transactional(transactionManager = KAFKA_TX_MANAGER_BEAN_NAME)
	public void sendCommittedPairDeclarative(String correlationId) {
		kafkaTemplate.send(KafkaTxTopics.DEMO_TX_A, correlationId, correlationId + "|c");
		kafkaTemplate.send(KafkaTxTopics.DEMO_TX_B, correlationId, correlationId + "|d");
		kafkaTemplate.flush();
	}

	/**
	 * 发送后抛出运行时异常，触发事务回滚；{@code read_committed} 消费者不应看到任一条。
	 *
	 * @param correlationId 关联 ID
	 */
	@Transactional(transactionManager = KAFKA_TX_MANAGER_BEAN_NAME)
	public void sendFailingPairDeclarative(String correlationId) {
		kafkaTemplate.send(KafkaTxTopics.DEMO_TX_A, correlationId, correlationId + "|c");
		kafkaTemplate.send(KafkaTxTopics.DEMO_TX_B, correlationId, correlationId + "|d");
		kafkaTemplate.flush();
		throw new IllegalStateException("simulated failure after sends (declarative @Transactional)");
	}
}
