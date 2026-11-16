package com.lin.spring.chat.service.fanout;

import com.lin.spring.chat.common.event.ConversationEvent;
import com.lin.spring.chat.service.repository.ConversationMemberRepository;
import com.lin.spring.chat.service.support.RelaySender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 会话事件消费者（Kafka conversation-events）：建群/邀请/移除/退群/改名/解散/撤回，
 * 推送给（解散前查到的）成员，客户端刷新会话列表与群信息；被移除/退群成员据此移除入口。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationEventFanoutConsumer {

    public static final String QUEUE_CONVERSATIONS = "/queue/conversations";

    private final ConversationMemberRepository memberRepository;
    private final RelaySender relaySender;

    @KafkaListener(topics = "conversation-events", groupId = "chat-conversations")
    public void onConversationEvent(ConversationEvent event) {
        List<String> memberIds = memberRepository.findByConversationId(event.conversationId()).stream()
                .map(m -> m.getUserId())
                .toList();
        for (String memberId : memberIds) {
            relaySender.relayToUser(memberId, QUEUE_CONVERSATIONS, event);
        }
        // 被移除者已不在成员表：对 MEMBER_REMOVED/MEMBER_LEFT/DISSOLVED 额外补发目标用户
        if (ConversationEvent.MEMBER_REMOVED.equals(event.action()) && event.refId() != null) {
            relaySender.relayToUser(event.refId(), QUEUE_CONVERSATIONS, event);
        }
        if (ConversationEvent.DISSOLVED.equals(event.action())) {
            // 解散后成员已删除，事件携带 convId，客户端按需清理（消息事件已先行广播系统提示）
            log.info("群已解散: conv={} actor={}", event.conversationId(), event.actorId());
        }
    }
}
