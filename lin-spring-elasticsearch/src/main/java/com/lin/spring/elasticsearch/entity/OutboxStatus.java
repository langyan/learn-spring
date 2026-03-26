package com.lin.spring.elasticsearch.entity;

/**
 * Outbox message status enum
 */
public enum OutboxStatus {
    /**
     * Message is pending to be published
     */
    PENDING,

    /**
     * Message has been successfully published
     */
    PUBLISHED,

    /**
     * Message publishing has failed
     */
    FAILED
}
