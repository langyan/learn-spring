package com.lin.spring.chat.service.fanout;

import com.lin.spring.chat.common.dto.MessageDto;
import com.lin.spring.chat.common.event.ChatMessageEvent;
import com.lin.spring.chat.service.entity.UndeliveredMessage;
import com.lin.spring.chat.service.repository.ConversationMemberRepository;
import com.lin.spring.chat.service.repository.UndeliveredMessageRepository;
import com.lin.spring.chat.service.support.RelaySender;
import com.lin.spring.chat.service.support.UnreadStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 消息扇出消费者（Kafka chat-messages，key=conversationId 保证会话内有序）：
 * 对会话每个成员（含发送者的其他设备，实现多端同步）：
 * - 未读数 +1（发送者除外）
 * - 离线 -> 写 undelivered_messages（唯一约束幂等），上线后经 /api/messages/undelivered 补投
 * - 在线 -> 定位其网关实例，发 per-gateway topic 完成最终 WebSocket 投递
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageFanoutConsumer {

    public static final String QUEUE_MESSAGES = "/queue/messages";

    private final ConversationMemberRepository memberRepository;
    private final UndeliveredMessageRepository undeliveredRepository;
    private final UnreadStore unreadStore;
    private final RelaySender relaySender;

    @KafkaListener(topics = "chat-messages", groupId = "chat-fanout")
    @Transactional
    public void onMessage(ChatMessageEvent event) {
        List<String> memberIds = memberRepository.findByConversationId(event.conversationId()).stream()
                .map(m -> m.getUserId())
                .toList();
        MessageDto payload = new MessageDto(event.id(), event.conversationId(), event.conversationType(),
                event.senderId(), event.senderNickname(), event.type(), event.content(),
                event.createdAtMillis(), event.recalled());

        for (String memberId : memberIds) {
            if (!memberId.equals(event.senderId())) {
                unreadStore.increment(memberId, event.conversationId());
            }
            relaySender.relayToUser(memberId, QUEUE_MESSAGES, payload);
            // relayToUser 内部查不到在线会话时静默跳过；离线落 undelivered 由下方统一兜底
        }

        // 离线成员补投暂存：无法在此区分"部分设备离线"，统一为无任何在线会话者落库
        List<String> offline = memberIds.stream()
                .filter(id -> relaySender.hasNoActiveSession(id))
                .toList();
        if (!offline.isEmpty()) {
            LocalDateTime now = LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(event.createdAtMillis()), ZoneOffset.UTC);
            for (String userId : offline) {
                try {
                    undeliveredRepository.save(UndeliveredMessage.builder()
                            .userId(userId)
                            .messageId(event.id())
                            .conversationId(event.conversationId())
                            .createdAt(now)
                            .build());
                } catch (Exception e) {
                    // 唯一约束冲突（重复消费/多端补偿）忽略，保证幂等
                    log.debug("undelivered 重复忽略: user={} msg={}", userId, event.id());
                }
            }
        }
        log.debug("扇出完成: conv={} members={} msg={}", event.conversationId(), memberIds.size(), event.id());
    }
}
