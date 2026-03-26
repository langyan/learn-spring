package com.lin.spring.elasticsearch.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "sync_logs")
public class SyncLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;

    @Enumerated(EnumType.STRING)
    private SyncOperation operation;

    @Enumerated(EnumType.STRING)
    private SyncStatus status;

    private String errorMessage;

    private Integer retryCount = 0;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum SyncOperation {
        CREATE, UPDATE, DELETE
    }

    public enum SyncStatus {
        PENDING, SUCCESS, FAILED
    }
}
