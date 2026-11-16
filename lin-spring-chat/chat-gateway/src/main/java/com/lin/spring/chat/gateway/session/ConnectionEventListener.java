package com.lin.spring.chat.gateway.session;

import com.lin.spring.chat.gateway.presence.PresenceHeartbeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * 连接生命周期：CONNECT 成功后登记会话映射（Redis），DISCONNECT 时注销并触发离线判定。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConnectionEventListener {

    private final SessionRegistry sessionRegistry;
    private final PresenceHeartbeatService presenceHeartbeatService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal user = accessor.getUser();
        String sessionId = accessor.getSessionId();
        if (user == null || sessionId == null) {
            return;
        }
        sessionRegistry.registerSession(user.getName(), sessionId);
        log.info("WS 连接: user={} session={} ", user.getName(), sessionId);
    }

    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        Principal user = event.getUser();
        String sessionId = event.getSessionId();
        if (user == null) {
            // 兜底：从消息头取
            SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.wrap(event.getMessage());
            user = accessor.getUser();
        }
        if (user == null || sessionId == null) {
            return;
        }
        sessionRegistry.removeSession(user.getName(), sessionId);
        presenceHeartbeatService.onDisconnect(user.getName());
        log.info("WS 断开: user={} session={}", user.getName(), sessionId);
    }
}
