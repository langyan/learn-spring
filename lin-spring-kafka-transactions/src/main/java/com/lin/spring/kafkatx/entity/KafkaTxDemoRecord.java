package com.lin.spring.kafkatx.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 「写库 + 发 Kafka」链式事务演示用实体。
 */
@Entity
@Table(name = "kafka_tx_demo_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class KafkaTxDemoRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 128)
	private String correlationId;

	@Column(nullable = false, length = 64)
	private String status;

	@Column(nullable = false)
	private Instant createdAt;
}
