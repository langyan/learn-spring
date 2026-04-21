package com.lin.spring.outbox.service;

import com.lin.spring.outbox.entity.OutboxMessage;
import com.lin.spring.outbox.entity.OutboxStatus;
import com.lin.spring.outbox.outbox.OutboxMessagePublisher;
import com.lin.spring.outbox.repository.OutboxMessageRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 中继失败重试与 FAILED 状态（不启动 Spring 容器）。
 */
@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

	@Mock
	private OutboxMessageRepository outboxMessageRepository;

	@Mock
	private OutboxMessagePublisher outboxMessagePublisher;

	@Test
	void processOne_whenPublishFails_shouldIncrementRetryAndEventuallyFail() throws Exception {
		OutboxRelayService relay = new OutboxRelayService(outboxMessageRepository, outboxMessagePublisher, 20, 2);

		OutboxMessage msg = new OutboxMessage("e1", "Order", "1", "OrderPlaced", "{}");
		msg.setId(10L);
		msg.setRetryCount(1);
		when(outboxMessageRepository.findById(10L)).thenReturn(Optional.of(msg));
		doThrow(new RuntimeException("broker down")).when(outboxMessagePublisher).publish(msg);

		relay.processOne(10L);

		ArgumentCaptor<OutboxMessage> cap = ArgumentCaptor.forClass(OutboxMessage.class);
		verify(outboxMessageRepository).save(cap.capture());
		assertThat(cap.getValue().getStatus()).isEqualTo(OutboxStatus.FAILED);
		assertThat(cap.getValue().getRetryCount()).isEqualTo(2);
	}

	@Test
	void processOne_whenPublishSucceeds_shouldMarkPublished() {
		OutboxRelayService relay = new OutboxRelayService(outboxMessageRepository, outboxMessagePublisher, 20, 3);

		OutboxMessage msg = new OutboxMessage("e2", "Order", "2", "OrderPlaced", "{}");
		msg.setId(20L);
		when(outboxMessageRepository.findById(20L)).thenReturn(Optional.of(msg));

		relay.processOne(20L);

		ArgumentCaptor<OutboxMessage> cap = ArgumentCaptor.forClass(OutboxMessage.class);
		verify(outboxMessageRepository).save(cap.capture());
		assertThat(cap.getValue().getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
	}

	@Test
	void processOne_whenNotPending_shouldNoop() throws Exception {
		OutboxRelayService relay = new OutboxRelayService(outboxMessageRepository, outboxMessagePublisher, 20, 3);

		OutboxMessage msg = new OutboxMessage("e3", "Order", "3", "OrderPlaced", "{}");
		msg.setId(30L);
		msg.setStatus(OutboxStatus.PUBLISHED);
		when(outboxMessageRepository.findById(30L)).thenReturn(Optional.of(msg));

		relay.processOne(30L);

		verify(outboxMessagePublisher, never()).publish(any());
		verify(outboxMessageRepository, never()).save(any());
	}
}
