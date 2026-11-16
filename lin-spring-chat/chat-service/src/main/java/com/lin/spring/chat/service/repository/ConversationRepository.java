package com.lin.spring.chat.service.repository;

import com.lin.spring.chat.service.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, String> {

    Optional<Conversation> findByDirectUserMinAndDirectUserMax(String min, String max);

    List<Conversation> findByIdInOrderById(List<String> ids);

    @Modifying
    @Query("update Conversation c set c.lastMessageId = :messageId, c.lastMessagePreview = :preview, c.lastMessageAt = :at where c.id = :conversationId")
    void updateLastMessage(@Param("conversationId") String conversationId,
                           @Param("messageId") String messageId,
                           @Param("preview") String preview,
                           @Param("at") java.time.LocalDateTime at);

    @Modifying
    @Query("update Conversation c set c.name = :name where c.id = :id")
    void rename(@Param("id") String id, @Param("name") String name);

    @Modifying
    @Query("update Conversation c set c.memberCount = c.memberCount + :delta where c.id = :id")
    void adjustMemberCount(@Param("id") String id, @Param("delta") int delta);
}
