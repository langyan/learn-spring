package com.lin.spring.outbox.controller;

import com.lin.spring.outbox.entity.OutboxMessage;
import com.lin.spring.outbox.entity.OutboxStatus;
import com.lin.spring.outbox.repository.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Outbox 只读查询（教学观察）。
 */
@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
public class OutboxController {

	private final OutboxMessageRepository outboxMessageRepository;

	/**
	 * 列出当前待投递的 Outbox 记录。
	 */
	@GetMapping("/pending")
	public List<OutboxMessage> listPending() {
		return outboxMessageRepository.findByStatusOrderByIdAsc(OutboxStatus.PENDING);
	}
}
