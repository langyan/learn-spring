package com.lin.spring.websocket.controller;

import com.lin.spring.websocket.config.RedisConfig;
import com.lin.spring.websocket.service.RedisPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final RedisPublisher redisPublisher;

    private final ChannelTopic topic;

    private final RedisTemplate<String, Object> redisTemplate;


    @PostMapping("/sendMessage")
    public ResponseEntity<String> sendMessage(@RequestBody String message) {
        redisPublisher.publish(topic.getTopic(), message);

        return ResponseEntity.ok("Message published to " + topic.getTopic()+" , message :"+message);
    }
    @PostMapping("/addStream")
    public ResponseEntity<String> addMessage(@RequestBody String message) {
        // 1. 将消息存入Stream
        ObjectRecord<String, Object> record = ObjectRecord.create(RedisConfig.DASHBOARD_EVENTS_STREAM, message);
        RecordId recordId = redisTemplate.opsForStream().add(record);

        return ResponseEntity.ok("add message to stream " + RedisConfig.DASHBOARD_EVENTS_STREAM+" , message :"+message);
    }
}
