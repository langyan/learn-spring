package com.lin.spring.chat.service.fanout;

import com.lin.spring.chat.common.event.PresenceEvent;
import com.lin.spring.chat.service.repository.ConversationMemberRepository;
import com.lin.spring.chat.service.support.RelaySender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 在线状态消费者（Kafka presence-events）：某用户上线/下线时，
 * 找出其全部会话的其他成员并推送状态变化（客户端更新会话列表绿点）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PresenceFanoutConsumer {

    public static final String QUEUE_PRESENCE = "/queue/presence";

    private final ConversationMemberRepository memberRepository;
    private final RelaySender relaySender;

    @KafkaListener(topics = "presence-events", groupId = "chat-presence")
    public void onPresence(PresenceEvent event) {
        List<String> conversationIds = memberRepository.findByUserId(event.userId()).stream()
                .map(m -> m.getConversationId())
                .toList();
        if (conversationIds.isEmpty()) {
            return;
        }
        List<String> relatedUserIds = memberRepository.findUserIdsOfConversations(conversationIds).stream()
                .filter(id -> !id.equals(event.userId()))
                .toList();
        for (String related : relatedUserIds) {
            relaySender.relayToUser(related, QUEUE_PRESENCE, event);
        }
        log.debug("状态扇出: user={} online={} 通知 {} 人", event.userId(), event.online(), relatedUserIds.size());
    }
}
