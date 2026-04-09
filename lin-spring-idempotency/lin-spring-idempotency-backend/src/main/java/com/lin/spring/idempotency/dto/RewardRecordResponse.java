package com.lin.spring.idempotency.dto;

import java.time.Instant;

public record RewardRecordResponse(
	String eventId,
	String orderNo,
	String action,
	String note,
	Instant processedAt
) {
}
