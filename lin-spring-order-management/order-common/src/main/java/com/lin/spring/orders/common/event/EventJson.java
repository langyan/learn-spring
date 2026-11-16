package com.lin.spring.orders.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.Map;

/**
 * 事件信封编解码：Kafka 上传输 UTF-8 JSON 字符串（StringSerializer/StringDeserializer），
 * 不依赖 Spring Kafka 的 JSON 序列化器配置，行为在所有服务间保持一致。
 */
public final class EventJson {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private EventJson() {
    }

    public static String encode(IntegrationEvent event) {
        try {
            return MAPPER.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode event " + event.eventId(), e);
        }
    }

    public static IntegrationEvent decode(String json) {
        try {
            return MAPPER.readValue(json, IntegrationEvent.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to decode event: " + json, e);
        }
    }

    public static Map<String, Object> decodePayload(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to decode payload: " + json, e);
        }
    }

    public static String encodePayload(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to encode payload", e);
        }
    }

    /** payload 数值统一按 BigDecimal 解析（金额在事件中以字符串传输，避免浮点精度问题）。 */
    public static java.math.BigDecimal amount(Map<String, Object> payload, String key) {
        Object v = payload.get(key);
        if (v == null) {
            throw new IllegalArgumentException("Missing payload key: " + key);
        }
        return new java.math.BigDecimal(v.toString());
    }
}
