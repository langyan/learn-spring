package com.lin.spring.websocket.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        System.out.println("Received message: " + new String(message.getBody()));
        messagingTemplate.convertAndSend("/topic/data", "from pub queue :" +new String(message.getBody()));
    }
}