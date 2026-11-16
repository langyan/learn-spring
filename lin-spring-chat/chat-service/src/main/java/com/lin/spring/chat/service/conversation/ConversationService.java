package com.lin.spring.chat.service.conversation;

import com.lin.spring.chat.common.event.ChatMessageEvent;
import com.lin.spring.chat.common.event.ConversationEvent;
import com.lin.spring.chat.service.common.ApiException;
import com.lin.spring.chat.service.entity.Conversation;
import com.lin.spring.chat.service.entity.ConversationMember;
import com.lin.spring.chat.service.entity.Message;
import com.lin.spring.chat.service.entity.User;
import com.lin.spring.chat.service.repository.ConversationMemberRepository;
import com.lin.spring.chat.service.repository.ConversationRepository;
import com.lin.spring.chat.service.repository.MessageRepository;
import com.lin.spring.chat.service.repository.UserRepository;
import com.lin.spring.chat.service.support.PresenceStore;
import com.lin.spring.chat.service.support.UnreadStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    public static final String TOPIC_CHAT_MESSAGES = "chat-messages";
    public static final String TOPIC_CONVERSATION_EVENTS = "conversation-events";

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository memberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UnreadStore unreadStore;
    private final PresenceStore presenceStore;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.group.max-members:500}")
    private int maxGroupMembers;

    // ---------- 视图 ----------

    public record MemberView(String userId, String username, String nickname, String role, boolean online) {
    }

    public record ConversationView(
            String id, String type, String name, String ownerId, int memberCount,
            String lastMessagePreview, Long lastMessageAtMillis,
            String peerId, String peerNickname, boolean peerOnline, // 单聊对方信息
            List<MemberView> members, // 详情时返回
            int unreadCount) {
    }

    // ---------- 单聊 ----------

    /** 发起单聊：同两人已有 DIRECT 会话则复用（唯一索引兜底防并发重复创建） */
    @Transactional
    public ConversationView createOrGetDirect(String me, String targetUserId) {
        if (me.equals(targetUserId)) {
            throw ApiException.badRequest("不能与自己发起会话");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> ApiException.notFound("对方用户不存在"));

        String min = me.compareTo(targetUserId) < 0 ? me : targetUserId;
        String max = me.compareTo(targetUserId) < 0 ? targetUserId : me;
        Optional<Conversation> existing = conversationRepository.findByDirectUserMinAndDirectUserMax(min, max);
        if (existing.isPresent()) {
            return detail(me, existing.get().getId());
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Conversation conversation = Conversation.builder()
                .id(UUID.randomUUID().toString())
                .type(Conversation.Type.DIRECT)
                .directUserMin(min)
                .directUserMax(max)
                .memberCount(2)
                .createdAt(now)
                .build();
        try {
            conversationRepository.saveAndFlush(conversation);
        } catch (Exception e) {
            // 并发兜底：唯一索引冲突则回查复用
            return conversationRepository.findByDirectUserMinAndDirectUserMax(min, max)
                    .map(c -> detail(me, c.getId()))
                    .orElseThrow(() -> new ApiException(500, "创建会话冲突，请重试"));
        }
        memberRepository.save(ConversationMember.builder()
                .conversationId(conversation.getId()).userId(min)
                .role(ConversationMember.Role.MEMBER).joinedAt(now).build());
        memberRepository.save(ConversationMember.builder()
                .conversationId(conversation.getId()).userId(max)
                .role(ConversationMember.Role.MEMBER).joinedAt(now).build());
        log.info("单聊创建: {} <-> {}", me, targetUserId);
        return detail(me, conversation.getId());
    }

    // ---------- 群聊 ----------

    @Transactional
    public ConversationView createGroup(String me, String name, List<String> memberIds) {
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("群名称不能为空");
        }
        LinkedHashSet<String> others = memberIds == null ? new LinkedHashSet<>() : new LinkedHashSet<>(memberIds);
        others.remove(me);
        if (others.isEmpty()) {
            throw ApiException.badRequest("请至少选择一名群成员");
        }
        int total = others.size() + 1;
        if (total > maxGroupMembers) {
            throw ApiException.badRequest("群成员上限 " + maxGroupMembers + " 人");
        }
        Map<String, User> users = userRepository.findAllByIdIn(List.copyOf(others)).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        if (users.size() != others.size()) {
            throw ApiException.badRequest("部分用户不存在");
        }
        User actor = userRepository.findById(me).orElseThrow(() -> ApiException.notFound("用户不存在"));

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Conversation conversation = Conversation.builder()
                .id(UUID.randomUUID().toString())
                .type(Conversation.Type.GROUP)
                .name(name.trim())
                .ownerId(me)
                .memberCount(total)
                .createdAt(now)
                .build();
        conversationRepository.save(conversation);
        memberRepository.save(ConversationMember.builder()
                .conversationId(conversation.getId()).userId(me)
                .role(ConversationMember.Role.OWNER).joinedAt(now).build());
        for (String userId : others) {
            memberRepository.save(ConversationMember.builder()
                    .conversationId(conversation.getId()).userId(userId)
                    .role(ConversationMember.Role.MEMBER).joinedAt(now).build());
        }

        // 系统消息：复用 messages 链路（保序/历史/离线补投天然一致）
        Message systemMessage = insertSystemMessage(conversation, actor, actor.getNickname() + " 创建了群聊", now);
        publishMessageEvent(conversation, systemMessage, actor.getNickname());
        publishConversationEvent(conversation.getId(), ConversationEvent.CREATED, me, null);
        log.info("建群: conv={} name={} members={}", conversation.getId(), conversation.getName(), total);
        return detail(me, conversation.getId());
    }

    @Transactional
    public ConversationView invite(String me, String conversationId, List<String> userIds) {
        Conversation conversation = requireMember(me, conversationId);
        requireOwner(me, conversation, "仅群主可邀请成员");
        if (userIds == null || userIds.isEmpty()) {
            throw ApiException.badRequest("请选择要邀请的用户");
        }
        LinkedHashSet<String> targets = new LinkedHashSet<>(userIds);
        List<ConversationMember> existing = memberRepository.findByConversationId(conversationId);
        int current = existing.size();
        if (current + targets.size() > maxGroupMembers) {
            throw ApiException.badRequest("群成员上限 " + maxGroupMembers + " 人");
        }
        Map<String, User> users = userRepository.findAllByIdIn(List.copyOf(targets)).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        if (users.size() != targets.size()) {
            throw ApiException.badRequest("部分用户不存在");
        }
        User actor = userRepository.findById(me).orElseThrow();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        List<String> added = new ArrayList<>();
        for (String userId : targets) {
            boolean already = existing.stream().anyMatch(m -> m.getUserId().equals(userId));
            if (already) {
                continue;
            }
            memberRepository.save(ConversationMember.builder()
                    .conversationId(conversationId).userId(userId)
                    .role(ConversationMember.Role.MEMBER).joinedAt(now).build());
            added.add(userId);
        }
        if (added.isEmpty()) {
            throw ApiException.badRequest("所选用户均已在群内");
        }
        conversationRepository.adjustMemberCount(conversationId, added.size());

        String names = added.stream().map(id -> users.get(id).getNickname()).collect(Collectors.joining("、"));
        Message systemMessage = insertSystemMessage(conversation, actor, actor.getNickname() + " 邀请 " + names + " 加入了群聊", now);
        publishMessageEvent(conversation, systemMessage, actor.getNickname());
        publishConversationEvent(conversationId, ConversationEvent.MEMBER_ADDED, me, String.join(",", added));
        return detail(me, conversationId);
    }

    @Transactional
    public void removeMember(String me, String conversationId, String targetUserId) {
        Conversation conversation = requireMember(me, conversationId);
        requireOwner(me, conversation, "仅群主可移除成员");
        if (targetUserId.equals(me)) {
            throw ApiException.badRequest("不能移除自己，请使用解散");
        }
        ConversationMember target = memberRepository.findByConversationIdAndUserId(conversationId, targetUserId)
                .orElseThrow(() -> ApiException.notFound("该成员不在群内"));
        memberRepository.delete(target);
        conversationRepository.adjustMemberCount(conversationId, -1);
        User actor = userRepository.findById(me).orElseThrow();
        User removed = userRepository.findById(targetUserId).orElseThrow();
        Message systemMessage = insertSystemMessage(conversation, actor,
                actor.getNickname() + " 将 " + removed.getNickname() + " 移出了群聊", LocalDateTime.now(ZoneOffset.UTC));
        publishMessageEvent(conversation, systemMessage, actor.getNickname());
        publishConversationEvent(conversationId, ConversationEvent.MEMBER_REMOVED, me, targetUserId);
    }

    @Transactional
    public void leave(String me, String conversationId) {
        Conversation conversation = requireMember(me, conversationId);
        if (conversation.getOwnerId() != null && conversation.getOwnerId().equals(me)) {
            throw ApiException.badRequest("群主不能退群，请使用解散群聊");
        }
        memberRepository.deleteByConversationIdAndUserId(conversationId, me);
        conversationRepository.adjustMemberCount(conversationId, -1);
        User actor = userRepository.findById(me).orElseThrow();
        Message systemMessage = insertSystemMessage(conversation, actor,
                actor.getNickname() + " 退出了群聊", LocalDateTime.now(ZoneOffset.UTC));
        publishMessageEvent(conversation, systemMessage, actor.getNickname());
        publishConversationEvent(conversationId, ConversationEvent.MEMBER_LEFT, me, me);
    }

    @Transactional
    public void dissolve(String me, String conversationId) {
        Conversation conversation = requireMember(me, conversationId);
        requireOwner(me, conversation, "仅群主可解散群聊");
        User actor = userRepository.findById(me).orElseThrow();
        Message systemMessage = insertSystemMessage(conversation, actor,
                actor.getNickname() + " 解散了群聊", LocalDateTime.now(ZoneOffset.UTC));
        publishMessageEvent(conversation, systemMessage, actor.getNickname());
        memberRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(conversation);
        publishConversationEvent(conversationId, ConversationEvent.DISSOLVED, me, null);
    }

    @Transactional
    public void rename(String me, String conversationId, String newName) {
        Conversation conversation = requireMember(me, conversationId);
        requireOwner(me, conversation, "仅群主可修改群名称");
        if (newName == null || newName.isBlank()) {
            throw ApiException.badRequest("群名称不能为空");
        }
        String name = newName.trim();
        conversationRepository.rename(conversationId, name);
        User actor = userRepository.findById(me).orElseThrow();
        Message systemMessage = insertSystemMessage(conversation, actor,
                actor.getNickname() + " 修改群名称为 \"" + name + "\"", LocalDateTime.now(ZoneOffset.UTC));
        publishMessageEvent(conversation, systemMessage, actor.getNickname());
        publishConversationEvent(conversationId, ConversationEvent.RENAMED, me, null);
    }

    // ---------- 查询 ----------

    /** 会话列表：按最后消息时间倒序，含未读数、单聊对方在线状态 */
    @Transactional(readOnly = true)
    public List<ConversationView> list(String me) {
        List<ConversationMember> memberships = memberRepository.findByUserId(me);
        if (memberships.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> unread = unreadStore.all(me);
        Map<String, User> userCache = new java.util.HashMap<>();

        return memberships.stream()
                .map(ConversationMember::getConversationId)
                .map(conversationRepository::findById)
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(Conversation::getLastMessageAt,
                        Comparator.nullsFirst(Comparator.naturalOrder())).reversed()
                        .thenComparing(Conversation::getCreatedAt, Comparator.reverseOrder()))
                .map(c -> toView(me, c, unread.getOrDefault(c.getId(), 0), userCache, false))
                .toList();
    }

    /** 会话详情（含成员列表与在线状态） */
    @Transactional(readOnly = true)
    public ConversationView detail(String me, String conversationId) {
        Conversation conversation = requireMember(me, conversationId);
        Map<String, Integer> unread = unreadStore.all(me);
        return toView(me, conversation, unread.getOrDefault(conversationId, 0), new java.util.HashMap<>(), true);
    }

    // ---------- 内部 ----------

    private Conversation requireMember(String userId, String conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> ApiException.notFound("会话不存在"));
        memberRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.forbidden("你不是该会话的成员"));
        return conversation;
    }

    private void requireOwner(String userId, Conversation conversation, String message) {
        if (conversation.getType() != Conversation.Type.GROUP) {
            throw ApiException.badRequest("仅群聊支持该操作");
        }
        if (!userId.equals(conversation.getOwnerId())) {
            throw ApiException.forbidden(message);
        }
    }

    private Message insertSystemMessage(Conversation conversation, User actor, String content, LocalDateTime now) {
        Message message = Message.builder()
                .id(UUID.randomUUID().toString())
                .conversationId(conversation.getId())
                .senderId(actor.getId())
                .type(Message.Type.SYSTEM)
                .content(content)
                .createdAt(now)
                .recalled(false)
                .build();
        messageRepository.save(message);
        conversationRepository.updateLastMessage(conversation.getId(), message.getId(),
                truncate(content), now);
        return message;
    }

    /** 发布消息事件到 chat-messages（key=conversationId 保证会话内有序） */
    public void publishMessageEvent(Conversation conversation, Message message, String senderNickname) {
        kafkaTemplate.send(TOPIC_CHAT_MESSAGES, conversation.getId(), new ChatMessageEvent(
                message.getId(), conversation.getId(), conversation.getType().name(),
                message.getSenderId(), senderNickname, message.getType().name(),
                message.getContent(), toMillis(message.getCreatedAt()), message.isRecalled()));
    }

    private void publishConversationEvent(String conversationId, String action, String actorId, String refId) {
        kafkaTemplate.send(TOPIC_CONVERSATION_EVENTS, conversationId,
                new ConversationEvent(conversationId, action, actorId, refId, System.currentTimeMillis()));
    }

    private ConversationView toView(String me, Conversation c, int unreadCount,
                                    Map<String, User> userCache, boolean withMembers) {
        String peerId = null;
        String peerNickname = null;
        boolean peerOnline = false;
        if (c.getType() == Conversation.Type.DIRECT) {
            peerId = c.getDirectUserMin().equals(me) ? c.getDirectUserMax() : c.getDirectUserMin();
            User peer = userCache.computeIfAbsent(peerId, id ->
                    userRepository.findById(id).orElse(null));
            if (peer != null) {
                peerNickname = peer.getNickname();
                peerOnline = presenceStore.isOnline(peerId);
            }
        }
        List<MemberView> members = null;
        if (withMembers) {
            List<String> memberIds = memberRepository.findByConversationId(c.getId()).stream()
                    .map(ConversationMember::getUserId).toList();
            Map<String, Boolean> onlineMap = presenceStore.onlineMap(memberIds);
            members = userRepository.findAllByIdIn(memberIds).stream()
                    .map(u -> new MemberView(u.getId(), u.getUsername(), u.getNickname(),
                            u.getId().equals(c.getOwnerId()) ? "OWNER" : "MEMBER",
                            onlineMap.getOrDefault(u.getId(), false)))
                    .toList();
        }
        String displayName = c.getType() == Conversation.Type.DIRECT ? peerNickname : c.getName();
        return new ConversationView(c.getId(), c.getType().name(), displayName, c.getOwnerId(),
                c.getMemberCount(), c.getLastMessagePreview(),
                c.getLastMessageAt() == null ? null : toMillis(c.getLastMessageAt()),
                peerId, peerNickname, peerOnline, members, unreadCount);
    }

    private static String truncate(String s) {
        return s != null && s.length() > 200 ? s.substring(0, 200) : s;
    }

    public static long toMillis(LocalDateTime time) {
        return time.toInstant(ZoneOffset.UTC).toEpochMilli();
    }
}
