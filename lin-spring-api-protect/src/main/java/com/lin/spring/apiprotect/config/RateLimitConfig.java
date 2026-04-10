package com.lin.spring.apiprotect.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class RateLimitConfig {

    @Bean
    public ConcurrentHashMap<String, RequestWindow> requestWindows() {
        return new ConcurrentHashMap<>();
    }

    public static final class RequestWindow {
        private final long windowStartEpochSecond;
        private final int count;

        public RequestWindow(long windowStartEpochSecond, int count) {
            this.windowStartEpochSecond = windowStartEpochSecond;
            this.count = count;
        }

        public long getWindowStartEpochSecond() {
            return windowStartEpochSecond;
        }

        public int getCount() {
            return count;
        }
    }
}
