package com.lin.spring.elasticsearch.repository;

import com.lin.spring.elasticsearch.entity.SyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {
    List<SyncLog> findByStatus(SyncLog.SyncStatus status);
    List<SyncLog> findByProductIdOrderByCreatedAtDesc(Long productId);
}
