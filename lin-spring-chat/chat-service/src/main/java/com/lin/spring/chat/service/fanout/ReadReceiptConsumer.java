package com.lin.spring.chat.service.fanout;

import com.lin.spring.chat.common.event.ReadReceiptEvent;
import com.lin.spring.chat.service.repository.ConversationMemberRepository;
import com.lin.spring.chat.service.support.RelaySender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 已读回执消费者（Kafka read-receipts）：把已读位置推送给会话内所有在线成员
 * （含阅读者自己的其他设备，多端已读状态同步）。发送方据此渲染单聊双勾/群聊已读数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadReceiptConsumer {

    public static final String QUEUE_RECEIPTS = "/queue/receipts";

    private final ConversationMemberRepository memberRepository;
    private final RelaySender relaySender;

    @KafkaListener(topics = "read-receipts", groupId = "chat-receipts")
    public void onReceipt(ReadReceiptEvent event) {
        List<String> memberIds = memberRepository.findByConversationId(event.conversationId()).stream()
                .map(m -> m.getUserId())
                .toList();
        for (String memberId : memberIds) {
            relaySender.relayToUser(memberId, QUEUE_RECEIPTS, event);
        }
    }
}
