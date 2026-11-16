package com.lin.spring.chat.service.repository;

import com.lin.spring.chat.service.entity.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationMemberRepository extends JpaRepository<ConversationMember, Long> {

    Optional<ConversationMember> findByConversationIdAndUserId(String conversationId, String userId);

    List<ConversationMember> findByConversationId(String conversationId);

    List<ConversationMember> findByUserId(String userId);

    @Query("select distinct m.conversationId from ConversationMember m where m.userId = :userId and m.conversationId in :conversationIds")
    List<String> findConversationIdsOfUser(@Param("userId") String userId, @Param("conversationIds") List<String> conversationIds);

    @Query("select distinct m.userId from ConversationMember m where m.conversationId in :conversationIds")
    List<String> findUserIdsOfConversations(@Param("conversationIds") List<String> conversationIds);

    void deleteByConversationIdAndUserId(String conversationId, String userId);

    void deleteByConversationId(String conversationId);

    @Modifying
    @Query("update ConversationMember m set m.lastReadMessageId = :messageId where m.conversationId = :conversationId and m.userId = :userId")
    void updateLastReadMessage(@Param("conversationId") String conversationId,
                               @Param("userId") String userId,
                               @Param("messageId") String messageId);
}
