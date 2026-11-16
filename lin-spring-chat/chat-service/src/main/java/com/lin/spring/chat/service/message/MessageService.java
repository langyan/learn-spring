package com.lin.spring.chat.service.message;

import com.lin.spring.chat.common.dto.MessageDto;
import com.lin.spring.chat.common.event.ChatMessageEvent;
import com.lin.spring.chat.common.event.ConversationEvent;
import com.lin.spring.chat.common.event.ReadReceiptEvent;
import com.lin.spring.chat.service.common.ApiException;
import com.lin.spring.chat.service.conversation.ConversationService;
import com.lin.spring.chat.service.entity.Conversation;
import com.lin.spring.chat.service.entity.ConversationMember;
import com.lin.spring.chat.service.entity.Message;
import com.lin.spring.chat.service.entity.UndeliveredMessage;
import com.lin.spring.chat.service.entity.User;
import com.lin.spring.chat.service.repository.ConversationMemberRepository;
import com.lin.spring.chat.service.repository.ConversationRepository;
import com.lin.spring.chat.service.repository.MessageRepository;
import com.lin.spring.chat.service.repository.UndeliveredMessageRepository;
import com.lin.spring.chat.service.repository.UserRepository;
import com.lin.spring.chat.service.support.ReadPositionStore;
import com.lin.spring.chat.service.support.UnreadStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private static final String TOPIC_CHAT_MESSAGES = "chat-messages";
    private static final String TOPIC_READ_RECEIPTS = "read-receipts";
    private static final String TOPIC_CONVERSATION_EVENTS = "conversation-events";
    private static final int MAX_CONTENT_LENGTH = 4000;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final UndeliveredMessageRepository undeliveredRepository;
    private final UserRepository userRepository;
    private final ReadPositionStore readPositionStore;
    private final UnreadStore unreadStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ConversationService conversationService;

    @Value("${app.recall.window-seconds:120}")
    private long recallWindowSeconds;

    // ---------- 发送 ----------

    /**
     * 发送消息主链路（对齐参考文章）：
     * 1. 校验成员身份 -> 2. 持久化 + 更新会话 last_message
     * -> 3. 发 Kafka chat-messages（key=conversationId，同会话固定分区保证有序）
     */
    public MessageDto send(String me, String conversationId, String content) {
        if (content == null || content.isBlank()) {
            throw ApiException.badRequest("消息内容不能为空");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw ApiException.badRequest("消息内容过长（上限 " + MAX_CONTENT_LENGTH + " 字符）");
        }
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("会话不存在"));
        memberRepository.findByConversationIdAndUserId(conversationId, me)
                .orElseThrow(() -> ApiException.forbidden("你不是该会话的成员"));
        User sender = userRepository.findById(me).orElseThrow(() -> ApiException.notFound("用户不存在"));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Message message = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversationId)
                .senderId(me)
                .type(Message.Type.TEXT)
                .content(content.trim())
                .createdAt(now)
                .recalled(false)
                .build();
        messageRepository.save(message);
        conversationRepository.updateLastMessage(conversationId, message.getId(), truncate(content.trim()), now);

        ChatMessageEvent event = new ChatMessageEvent(message.getId(), conversationId,
                conversation.getType().name(), me, sender.getNickname(),
                Message.Type.TEXT.name(), message.getContent(),
                ConversationService.toMillis(now), false);
        kafkaTemplate.send(TOPIC_CHAT_MESSAGES, conversationId, event);
        return toDto(conversation.getType().name(), sender.getNickname(), message);
    }

    // ---------- 历史 ----------

    /** keyset 分页：传上一页最早消息的 createdAt 毫秒 + id；返回时间升序，可直接渲染 */
    @Transactional(readOnly = true)
    public List<MessageDto> history(String me, String conversationId, Long beforeMillis, String beforeId, int size) {
        Conversation conversation = requireMembership(me, conversationId);
        LocalDateTime before = beforeMillis == null ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(beforeMillis), ZoneOffset.UTC);
        Pageable pageable = PageRequest.of(0, Math.min(Math.max(size, 1), 50));
        List<Message> page = messageRepository.findPage(conversationId, before, beforeId, pageable);

        Map<String, String> nicknames = nicknameCache(page);
        List<MessageDto> ascending = page.stream()
                .map(m -> toDto(conversation.getType().name(),
                        nicknames.getOrDefault(m.getSenderId(), ""), m))
                .collect(Collectors.toCollection(ArrayList::new));
        // DB 按时间倒序取页，翻转为升序
        ascending.sort(Comparator.comparingLong(MessageDto::createdAtMillis).thenComparing(MessageDto::id));
        return ascending;
    }

    // ---------- 撤回 ----------

    @Transactional
    public void recall(String me, String messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ApiException.notFound("消息不存在"));
        if (!me.equals(message.getSenderId())) {
            throw ApiException.forbidden("仅发送者可撤回消息");
        }
        if (message.isRecalled()) {
            return; // 幂等
        }
        if (message.getType() != Message.Type.TEXT) {
            throw ApiException.badRequest("该消息类型不支持撤回");
        }
        long ageMillis = System.currentTimeMillis() - ConversationService.toMillis(message.getCreatedAt());
        if (ageMillis > recallWindowSeconds * 1000) {
            throw ApiException.badRequest("消息发出超过 " + recallWindowSeconds / 60 + " 分钟，无法撤回");
        }
        messageRepository.markRecalled(messageId);
        // 撤回后消息仍保留在历史中（recalled=1），客户端气泡替换为系统文案
        kafkaTemplate.send(TOPIC_CONVERSATION_EVENTS, message.getConversationId(),
                new ConversationEvent(message.getConversationId(), ConversationEvent.MESSAGE_RECALLED,
                        me, messageId, System.currentTimeMillis()));
    }

    // ---------- 已读 ----------

    /** 上报已读位置：Redis 位置 + MySQL 冗余 + 清未读 + Kafka 通知（单聊双勾/群已读数） */
    @Transactional
    public void markRead(String me, String conversationId, String lastReadMessageId) {
        requireMembership(me, conversationId);
        Message message = messageRepository.findById(lastReadMessageId)
                .orElseThrow(() -> ApiException.notFound("消息不存在"));
        if (!message.getConversationId().equals(conversationId)) {
            throw ApiException.badRequest("消息不属于该会话");
        }
        long atMillis = ConversationService.toMillis(message.getCreatedAt());
        readPositionStore.set(conversationId, me, atMillis);
        memberRepository.updateLastReadMessage(conversationId, me, lastReadMessageId);
        unreadStore.clear(me, conversationId);
        kafkaTemplate.send(TOPIC_READ_RECEIPTS, conversationId, new ReadReceiptEvent(
                conversationId, me, lastReadMessageId, atMillis, System.currentTimeMillis()));
    }

    /** 消息已读状态（群聊已读成员昵称/单聊是否已读） */
    @Transactional(readOnly = true)
    public ReadStatusView readStatus(String me, String conversationId, String messageId) {
        requireMembership(me, conversationId);
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> ApiException.notFound("消息不存在"));
        long atMillis = ConversationService.toMillis(message.getCreatedAt());
        List<String> memberIds = memberRepository.findByConversationId(conversationId).stream()
                .map(ConversationMember::getUserId).toList();
        Map<String, Long> positions = readPositionStore.positions(conversationId, memberIds);
        Map<String, String> nicknames = nicknameCache(memberIds.stream()
                .map(id -> Message.builder().id("x").senderId(id).build()).toList());
        List<String> readBy = memberIds.stream()
                .filter(id -> !id.equals(message.getSenderId()))
                .filter(id -> positions.getOrDefault(id, 0L) >= atMillis)
                .map(id -> nicknames.getOrDefault(id, id))
                .toList();
        return new ReadStatusView(readBy.size(), readBy);
    }

    public record ReadStatusView(int readCount, List<String> readByNicknames) {
    }

    // ---------- 离线补投 ----------

    /** 上线拉取离线期间暂存消息并清空（时间升序） */
    @Transactional
    public List<MessageDto> pullUndelivered(String me) {
        List<UndeliveredMessage> rows = undeliveredRepository.findTop500ByUserIdOrderByCreatedAtAsc(me);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> messageIds = rows.stream().map(UndeliveredMessage::getMessageId).toList();
        List<Message> messages = messageRepository.findAllByIdIn(messageIds).stream()
                .sorted(Comparator.comparing(Message::getCreatedAt).thenComparing(Message::getId))
                .toList();
        Map<String, String> conversationTypes = new LinkedHashMap<>();
        Map<String, String> nicknames = nicknameCache(messages);
        List<MessageDto> result = new ArrayList<>();
        for (Message m : messages) {
            String type = conversationTypes.computeIfAbsent(m.getConversationId(), id ->
                    conversationRepository.findById(id).map(c -> c.getType().name()).orElse("GROUP"));
            result.add(toDto(type, nicknames.getOrDefault(m.getSenderId(), ""), m));
        }
        undeliveredRepository.deleteByUserId(me);
        log.info("离线补投: user={} count={}", me, result.size());
        return result;
    }

    // ---------- 内部 ----------

    private Conversation requireMembership(String userId, String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("会话不存在"));
        memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.forbidden("你不是该会话的成员"));
        return conversation;
    }

    private Map<String, String> nicknameCache(List<Message> messages) {
        List<String> senderIds = messages.stream().map(Message::getSenderId).filter(java.util.Objects::nonNull).distinct().toList();
        if (senderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRepository.findAllByIdIn(senderIds).stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));
    }

    private MessageDto toDto(String conversationType, String senderNickname, Message m) {
        return new MessageDto(m.getId(), m.getConversationId(), conversationType,
                m.getSenderId(), senderNickname, m.getType().name(), m.getContent(),
                ConversationService.toMillis(m.getCreatedAt()), m.isRecalled());
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) : s;
    }
}
