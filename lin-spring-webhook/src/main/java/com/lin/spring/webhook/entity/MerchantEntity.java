package com.lin.spring.webhook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 商户。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "merchants")
public class MerchantEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, unique = true, length = 64)
	private String merchantId;

	@Column(length = 128)
	private String name;

	@Column(nullable = false)
	private Instant createdAt = Instant.now();
}
