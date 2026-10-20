package com.lin.spring.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * TTL of the Redis authority cache (key: auth:authorities:{username}).
 *
 * <p>Shorter TTL = faster permission propagation after direct DB changes,
 * higher Redis/DB chatter. 5-15 minutes is a common production range.
 *
 * @param ttl how long a user's authority set stays cached
 */
@ConfigurationProperties(prefix = "app.authority-cache")
public record AuthorityCacheProperties(Duration ttl) {
}
