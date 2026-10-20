package com.lin.spring.webhook.dto;

import com.lin.spring.webhook.domain.OutcomeKind;

/**
 * 投递编排层对一次任务的处置结论。
 */
public record DeliveryOutcome(OutcomeKind kind, String reason) {

	public static DeliveryOutcome of(OutcomeKind kind, String reason) {
		return new DeliveryOutcome(kind, reason);
	}
}
