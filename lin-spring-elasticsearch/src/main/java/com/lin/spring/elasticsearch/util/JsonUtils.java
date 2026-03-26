package com.lin.spring.elasticsearch.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * Utility class for JSON operations
 */
@Slf4j
public class JsonUtils {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private JsonUtils() {
        // Utility class
    }

    /**
     * Convert object to JSON string
     */
    public static String toJson(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.error("Error converting object to JSON", e);
            return "{}";
        }
    }

    /**
     * Convert JSON string to object
     */
    public static <T> T fromJson(String json, Class<T> clazz) {
        try {
            return OBJECT_MAPPER.readValue(json, clazz);
        } catch (JsonProcessingException e) {
            log.error("Error converting JSON to object", e);
            return null;
        }
    }

    /**
     * Get ObjectMapper instance
     */
    public static ObjectMapper getObjectMapper() {
        return OBJECT_MAPPER;
    }
}
