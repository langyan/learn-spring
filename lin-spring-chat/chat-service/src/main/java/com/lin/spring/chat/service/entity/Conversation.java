package com.lin.spring.chat.service.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    public enum Type { DIRECT, GROUP }

    @Id
    private String id;
    @Enumerated(EnumType.STRING)
    private Type type;
    private String name;                 // 群名；DIRECT 为空
    private String ownerId;              // 群主；DIRECT 为空
    private String directUserMin;        // 单聊成员对（排序后），唯一索引兜底防并发重复创建
    private String directUserMax;
    private String lastMessageId;
    private String lastMessagePreview;   // 列表预览（冗余，避免列表页回查消息表）
    private LocalDateTime lastMessageAt;
    private int memberCount;
    private LocalDateTime createdAt;
}
