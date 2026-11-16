package com.lin.spring.chat.service.repository;

import com.lin.spring.chat.service.entity.UndeliveredMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UndeliveredMessageRepository extends JpaRepository<UndeliveredMessage, Long> {

    List<UndeliveredMessage> findTop500ByUserIdOrderByCreatedAtAsc(String userId);

    void deleteByUserId(String userId);

    @Query("select count(u) > 0 from UndeliveredMessage u where u.userId = :userId and u.messageId = :messageId")
    boolean existsByUserIdAndMessageId(@Param("userId") String userId, @Param("messageId") String messageId);
}
