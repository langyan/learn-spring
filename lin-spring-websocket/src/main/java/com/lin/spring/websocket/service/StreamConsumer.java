package com.lin.spring.websocket.service;

import com.lin.spring.websocket.config.RedisConfig;
import jakarta.annotation.PostConstruct;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class StreamConsumer {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public StreamConsumer(RedisTemplate<String, Object> redisTemplate, SimpMessagingTemplate messagingTemplate) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
    }

//    @PostConstruct
//    public void consumeStream() {
//        redisTemplate.opsForStream()
//                .read(StreamReadOptions.empty(), StreamOffset.latest("dashboard:events"))
//                .forEach(record -> messagingTemplate.convertAndSend("/topic/data", record.getValue()));
//    }

    @Scheduled(fixedDelay = 1000)
    public void consume() {
        List<MapRecord<String, Object, Object>> messages = redisTemplate.opsForStream()
                .read(StreamReadOptions.empty().count(1).block(Duration.ofMillis(1000)),
                        StreamOffset.fromStart(RedisConfig.DASHBOARD_EVENTS_STREAM));

        for (MapRecord<String, Object, Object> msg : messages) {
//            messagingTemplate.convertAndSend("/topic/data", "from stream :" +msg.getValue());
        }
    }
}