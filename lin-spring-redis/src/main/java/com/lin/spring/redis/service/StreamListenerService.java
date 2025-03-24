package com.lin.spring.redis.service;

import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

@Service
public class StreamListenerService {

    private final RedisTemplate<String, String> redisTemplate;

    public StreamListenerService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @PostConstruct
    public void consumeStream() {
        redisTemplate.opsForStream()
            .read(StreamReadOptions.empty(), StreamOffset.latest("my-stream"))
            .forEach(record -> System.out.println("Received: " + record.getValue()));
    }
}
