package com.lin.spring.orders.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * 事务性 Outbox 事件：与业务记录同事务写入，发布器异步投递（可能重复投递，消费者幂等）。
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEvent {

    @Id
    private UUID id;

    /** 聚合根（订单）ID，同时作为 Kafka 分区键。 */
    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "customer_id")
    private Long customerId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    /** 聚合内单调递增序号，用于乱序检测。 */
    @Column(nullable = false)
    private Long sequence;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private String payload;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "published_at")
    private Instant publishedAt;
}
