package com.lin.spring.scheduler.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 轻量 JSON 编解码器：Kafka 传输统一走 String 序列化，避免依赖各框架自带
 * ObjectMapper 的版本差异（记录类型 + Instant/ISO-8601 均支持）。
 */
public final class JsonCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private JsonCodec() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("JSON serialize failed", e);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("JSON deserialize failed", e);
        }
    }
}
