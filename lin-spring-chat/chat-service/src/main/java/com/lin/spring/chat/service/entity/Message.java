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
@Table(name = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    public enum Type { TEXT, SYSTEM }

    @Id
    private String id;
    private String conversationId;
    private String senderId;          // SYSTEM 消息为操作发起者
    @Enumerated(EnumType.STRING)
    private Type type;
    private String content;
    private LocalDateTime createdAt;  // UTC
    private boolean recalled;
}
