package com.lin.spring.kafkatx.repository;

import com.lin.spring.kafkatx.entity.KafkaTxDemoRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@link KafkaTxDemoRecord} 持久化。
 */
public interface KafkaTxDemoRecordRepository extends JpaRepository<KafkaTxDemoRecord, Long> {

	/**
	 * 按关联 ID 查询。
	 *
	 * @param correlationId 关联 ID
	 * @return 可能为空的记录
	 */
	Optional<KafkaTxDemoRecord> findByCorrelationId(String correlationId);
}
