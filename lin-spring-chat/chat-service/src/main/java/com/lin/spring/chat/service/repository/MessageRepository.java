package com.lin.spring.chat.service.repository;

import com.lin.spring.chat.service.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, String> {

    /**
     * keyset 分页：created_at + id 双游标避免深分页 OFFSET 与同毫秒乱序。
     */
    @Query("select m from Message m where m.conversationId = :conversationId" +
            " and (:before is null or m.createdAt < :before or (m.createdAt = :before and m.id < :beforeId))" +
            " order by m.createdAt desc, m.id desc")
    List<Message> findPage(@Param("conversationId") String conversationId,
                           @Param("before") LocalDateTime before,
                           @Param("beforeId") String beforeId,
                           Pageable pageable);

    @Query("select m from Message m where m.id in :ids")
    List<Message> findAllByIdIn(@Param("ids") List<String> ids);

    @Modifying
    @Query("update Message m set m.recalled = true where m.id = :id")
    void markRecalled(@Param("id") String id);
}
