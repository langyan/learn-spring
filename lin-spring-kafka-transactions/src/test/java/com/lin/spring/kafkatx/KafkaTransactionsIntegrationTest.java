package com.lin.spring.kafkatx;

import com.lin.spring.kafkatx.config.KafkaTxTopics;
import com.lin.spring.kafkatx.listener.DemoEventsListener;
import com.lin.spring.kafkatx.service.TransactionalDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 嵌入式 Kafka 下验证：事务提交双 Topic 对 {@code read_committed} 可见；异常则均不可见。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@EmbeddedKafka(
	partitions = 1,
	topics = {KafkaTxTopics.DEMO_TX_A, KafkaTxTopics.DEMO_TX_B},
	brokerProperties = {
		"transaction.state.log.replication.factor=1",
		"transaction.state.log.min.isr=1"
	}
)
class KafkaTransactionsIntegrationTest {

	@Autowired
	private TransactionalDemoService transactionalDemoService;

	@Autowired
	private DemoEventsListener demoEventsListener;

	@BeforeEach
	void setUp() {
		demoEventsListener.clear();
	}

	@Test
	void committedTransaction_deliversBothTopics() throws Exception {
		String correlationId = "ok-" + UUID.randomUUID();

		transactionalDemoService.sendCommittedPair(correlationId);

		Set<String> topics = new HashSet<>();
		for (int i = 0; i < 2; i++) {
			DemoEventsListener.ReceivedRecord r =
				demoEventsListener.getReceived().poll(15, TimeUnit.SECONDS);
			assertThat(r).as("record %d", i).isNotNull();
			assertThat(r.value()).startsWith(correlationId + "|");
			topics.add(r.topic());
		}
		assertThat(topics).containsExactlyInAnyOrder(KafkaTxTopics.DEMO_TX_A, KafkaTxTopics.DEMO_TX_B);
	}

	@Test
	void abortedTransaction_deliversNothing() throws Exception {
		String correlationId = "fail-" + UUID.randomUUID();

		assertThatThrownBy(() -> transactionalDemoService.sendFailingPair(correlationId))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("simulated failure");

		DemoEventsListener.ReceivedRecord noise =
			demoEventsListener.getReceived().poll(5, TimeUnit.SECONDS);
		assertThat(noise).as("read_committed 消费者不应收到已中止事务的消息").isNull();
	}
}
