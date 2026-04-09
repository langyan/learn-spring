package com.lin.spring.idempotency.repository;

import com.lin.spring.idempotency.entity.RewardRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardRecordRepository extends JpaRepository<RewardRecord, Long> {

	List<RewardRecord> findByOrderNoOrderByProcessedAtAsc(String orderNo);
}
