package com.lin.spring.chat.gateway.config;

import com.lin.spring.chat.common.jwt.JwtSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * STOMP CONNECT 帧鉴权：校验 Authorization: Bearer {token}（JWT，与 chat-service 共享密钥），
 * 通过后以 userId 作为 Principal，后续 convertAndSendToUser 依赖该身份路由到用户队列。
 */
@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private final JwtSupport jwtSupport;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith("Bearer ")) {
                throw new IllegalArgumentException("缺少 Authorization 头");
            }
            String userId = jwtSupport.verify(header.substring(7)); // 失败抛 JwtException -> 拒绝连接
            accessor.setUser(new ChatPrincipal(userId));
        }
        return message;
    }

    public record ChatPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
