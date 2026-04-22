package com.lin.spring.kafkatx.service;

import com.lin.spring.kafkatx.config.ChainedJpaKafkaTransactionConfiguration;
import com.lin.spring.kafkatx.config.KafkaTxTopics;
import com.lin.spring.kafkatx.entity.KafkaTxDemoRecord;
import com.lin.spring.kafkatx.repository.KafkaTxDemoRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 在同一 Spring 事务边界内<strong>写 H2 表并发送 Kafka</strong>：使用链式
 * {@link org.springframework.data.transaction.ChainedTransactionManager} 组合
 * {@link org.springframework.kafka.transaction.KafkaTransactionManager} 与
 * {@link org.springframework.orm.jpa.JpaTransactionManager}。
 * <p>与仅 Kafka 的 {@link TransactionalDeclarativeDemoService}、
 * {@link TransactionalDemoService} 区分；Kafka 消息 value 后缀为 {@code |db}。</p>
 */
@Service
@RequiredArgsConstructor
public class DbAndKafkaChainedDemoService {

	private final KafkaTxDemoRecordRepository recordRepository;

	private final KafkaTemplate<String, String> kafkaTemplate;

	/**
	 * 插入记录并发送 Kafka，整体提交。
	 *
	 * @param correlationId 关联 ID（唯一）
	 */
	@Transactional(transactionManager = ChainedJpaKafkaTransactionConfiguration.CHAINED_TRANSACTION_MANAGER_BEAN_NAME)
	public void saveAndPublish(String correlationId) {
		recordRepository.save(new KafkaTxDemoRecord(
			null,
			correlationId,
			"RECORDED",
			Instant.now()
		));
		kafkaTemplate.send(KafkaTxTopics.DEMO_TX_DB, correlationId, correlationId + "|db");
		kafkaTemplate.flush();
	}

	/**
	 * 插入并发送后抛错，触发整段链式事务回滚（库与 Kafka 均不应留下该次提交）。
	 *
	 * @param correlationId 关联 ID
	 */
	@Transactional(transactionManager = ChainedJpaKafkaTransactionConfiguration.CHAINED_TRANSACTION_MANAGER_BEAN_NAME)
	public void saveAndPublishFailing(String correlationId) {
		recordRepository.save(new KafkaTxDemoRecord(
			null,
			correlationId,
			"SHOULD_ROLLBACK",
			Instant.now()
		));
		kafkaTemplate.send(KafkaTxTopics.DEMO_TX_DB, correlationId, correlationId + "|db");
		kafkaTemplate.flush();
		throw new IllegalStateException("simulated failure after DB + Kafka sends (chained)");
	}
}
