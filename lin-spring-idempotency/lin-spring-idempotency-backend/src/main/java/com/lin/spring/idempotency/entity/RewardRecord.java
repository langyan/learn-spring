package com.lin.spring.idempotency.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "reward_records")
public class RewardRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String eventId;

	@Column(nullable = false)
	private String orderNo;

	@Column(nullable = false)
	private String action;

	@Column(nullable = false)
	private String note;

	@Column(nullable = false)
	private Instant processedAt;

	public Long getId() {
		return id;
	}

	public String getEventId() {
		return eventId;
	}

	public void setEventId(String eventId) {
		this.eventId = eventId;
	}

	public String getOrderNo() {
		return orderNo;
	}

	public void setOrderNo(String orderNo) {
		this.orderNo = orderNo;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getNote() {
		return note;
	}

	public void setNote(String note) {
		this.note = note;
	}

	public Instant getProcessedAt() {
		return processedAt;
	}

	public void setProcessedAt(Instant processedAt) {
		this.processedAt = processedAt;
	}
}
