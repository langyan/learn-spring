package com.lin.spring.chat.gateway.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/** 客户端上行：STOMP SEND /app/heartbeat（应用层心跳，驱动 Redis ZSet 在线状态） */
@Controller
@RequiredArgsConstructor
public class HeartbeatHandler {

    private final PresenceHeartbeatService presenceHeartbeatService;

    @MessageMapping("/heartbeat")
    public void heartbeat(SimpMessageHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user != null) {
            presenceHeartbeatService.heartbeat(user.getName());
        }
    }
}
