package com.lin.spring.idempotency.consumer;

import com.lin.spring.idempotency.entity.RewardRecord;
import com.lin.spring.idempotency.event.OrderCreatedEvent;
import com.lin.spring.idempotency.repository.RewardRecordRepository;
import com.lin.spring.idempotency.service.EventDedupeStore;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class OrderCreatedEventConsumer {

	private final EventDedupeStore eventDedupeStore;
	private final RewardRecordRepository rewardRecordRepository;

	public OrderCreatedEventConsumer(
		EventDedupeStore eventDedupeStore,
		RewardRecordRepository rewardRecordRepository
	) {
		this.eventDedupeStore = eventDedupeStore;
		this.rewardRecordRepository = rewardRecordRepository;
	}

	@KafkaListener(
		topics = "${app.kafka.topics.order-created}",
		groupId = "${spring.kafka.consumer.group-id}",
		containerFactory = "kafkaListenerContainerFactory"
	)
	public void consume(OrderCreatedEvent event, Acknowledgment acknowledgment) {
		if (eventDedupeStore.isProcessed(event.eventId())) {
			acknowledgment.acknowledge();
			return;
		}

		if (!eventDedupeStore.claim(event.eventId(), event.orderNo())) {
			acknowledgment.acknowledge();
			return;
		}

		try {
			RewardRecord rewardRecord = new RewardRecord();
			rewardRecord.setEventId(event.eventId());
			rewardRecord.setOrderNo(event.orderNo());
			rewardRecord.setAction("GRANT_REWARD_POINTS");
			rewardRecord.setNote("Reward points granted exactly once for event " + event.eventId());
			rewardRecord.setProcessedAt(Instant.now());
			rewardRecordRepository.save(rewardRecord);

			acknowledgment.acknowledge();
		} catch (RuntimeException ex) {
			eventDedupeStore.release(event.eventId());
			throw ex;
		}
	}
}
