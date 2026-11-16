package com.lin.spring.events.config;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * kafka profile 下的生产者/消费者装配（与 lin-spring-outbox、lin-spring-notification 约定一致）。
 * <ul>
 *   <li>生产者：acks=all + 幂等发送，杜绝 broker 侧消息丢失</li>
 *   <li>消费者：关闭自动提交 + AckMode.MANUAL，处理成功才 ack，
 *       崩溃/超时后由容器重投，构成 at-least-once，由 Inbox 幂等兜底</li>
 * </ul>
 */
@Configuration
@Profile("kafka")
@RequiredArgsConstructor
public class KafkaConfig {

	private final EventsProperties properties;

	@Bean
	public ProducerFactory<String, String> eventsProducerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
			kafkaBootstrapServers());
		config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
		config.put(ProducerConfig.ACKS_CONFIG, "all");
		config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
		return new DefaultKafkaProducerFactory<>(config);
	}

	/**
	 * bootstrap 地址单独暴露为 Bean，便于测试与属性占位复用。
	 */
	@Bean
	public String kafkaBootstrapServers() {
		return System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "127.0.0.1:9092");
	}

	@Bean
	public KafkaTemplate<String, String> eventsKafkaTemplate() {
		return new KafkaTemplate<>(eventsProducerFactory());
	}

	@Bean
	public ConsumerFactory<String, String> eventsConsumerFactory() {
		Map<String, Object> config = new HashMap<>();
		config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaBootstrapServers());
		config.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getKafka().getConsumerGroup());
		config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
		config.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
		config.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, StringDeserializer.class);
		// 关闭自动提交：处理成功才手动 ack
		config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
		config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		return new DefaultKafkaConsumerFactory<>(config);
	}

	@Bean
	public ConcurrentKafkaListenerContainerFactory<String, String> eventsKafkaListenerContainerFactory() {
		ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
		factory.setConsumerFactory(eventsConsumerFactory());
		factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
		return factory;
	}

	/**
	 * 抑制未使用告警：JsonDeserializer 在此处不直接使用，保留常量引用供文档说明。
	 */
	@SuppressWarnings("unused")
	private static final String TRUSTED_PACKAGES = JsonDeserializer.VALUE_DEFAULT_TYPE;
}
