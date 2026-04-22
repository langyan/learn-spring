package com.lin.spring.kafkatx.config;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.transaction.ChainedTransactionManager;
import org.springframework.kafka.transaction.KafkaTransactionManager;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 显式提供名为 {@code transactionManager} 的 {@link JpaTransactionManager}（供 Spring Data JPA 与默认
 * {@code @Transactional} 解析），并注册链式管理器将 Kafka 与 JPA 纳入同一 Spring 事务边界。
 * <p><strong>局限：</strong>链式管理器不是 2PC；生产环境更稳妥见 {@code lin-spring-outbox}。</p>
 * <p>{@link ChainedTransactionManager} 已过时，此处仅作学习对照。</p>
 */
@Configuration
@SuppressWarnings("deprecation")
public class ChainedJpaKafkaTransactionConfiguration {

	/**
	 * Bean 名称，供业务 {@code @Transactional(transactionManager = "...")} 引用。
	 */
	public static final String CHAINED_TRANSACTION_MANAGER_BEAN_NAME = "chainedTransactionManager";

	/**
	 * Spring Data JPA 默认按 Bean 名 {@code transactionManager} 查找事务管理器。
	 *
	 * @param entityManagerFactory 持久化单元工厂
	 * @return JPA 事务管理器
	 */
	@Bean(name = "transactionManager")
	@Primary
	public JpaTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
		return new JpaTransactionManager(entityManagerFactory);
	}

	@Bean(name = CHAINED_TRANSACTION_MANAGER_BEAN_NAME)
	public PlatformTransactionManager chainedTransactionManager(
		@Qualifier("transactionManager") JpaTransactionManager jpaTransactionManager,
		@Qualifier("kafkaTransactionManager") KafkaTransactionManager kafkaTransactionManager
	) {
		// 开启顺序：Kafka → JPA；提交顺序（逆序）：JPA 先提交，Kafka 后提交
		return new ChainedTransactionManager(kafkaTransactionManager, jpaTransactionManager);
	}
}
