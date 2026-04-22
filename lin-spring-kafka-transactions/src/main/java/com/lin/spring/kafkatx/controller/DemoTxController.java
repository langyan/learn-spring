package com.lin.spring.kafkatx.controller;

import com.lin.spring.kafkatx.service.DbAndKafkaChainedDemoService;
import com.lin.spring.kafkatx.service.TransactionalDeclarativeDemoService;
import com.lin.spring.kafkatx.service.TransactionalDemoService;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 手动触发事务提交 / 中止的 REST 接口。
 * <p>路径 {@code /api/tx/...} 使用 {@link org.springframework.kafka.core.KafkaTemplate#executeInTransaction}；
 * {@code /api/tx/declarative/...} 使用 {@code @Transactional(transactionManager = "kafkaTransactionManager")}；
 * {@code /api/tx/db-kafka/...} 使用链式 {@code chainedTransactionManager}（JPA + Kafka）。</p>
 */
@Validated
@RestController
@RequestMapping("/api/tx")
@RequiredArgsConstructor
public class DemoTxController {

	private final TransactionalDemoService transactionalDemoService;

	private final TransactionalDeclarativeDemoService transactionalDeclarativeDemoService;

	private final DbAndKafkaChainedDemoService dbAndKafkaChainedDemoService;

	/**
	 * 提交双 Topic 事务。
	 *
	 * @param correlationId 关联 ID
	 */
	@PostMapping("/commit/{correlationId}")
	public ResponseEntity<Map<String, String>> commit(@PathVariable @NotBlank String correlationId) {
		transactionalDemoService.sendCommittedPair(correlationId);
		return ResponseEntity.ok(Map.of("status", "committed", "correlationId", correlationId));
	}

	/**
	 * 发送后失败，中止事务。
	 *
	 * @param correlationId 关联 ID
	 */
	@PostMapping("/rollback/{correlationId}")
	public ResponseEntity<Map<String, String>> rollback(@PathVariable @NotBlank String correlationId) {
		try {
			transactionalDemoService.sendFailingPair(correlationId);
			return ResponseEntity.internalServerError().body(Map.of("status", "unexpected-success"));
		}
		catch (IllegalStateException ex) {
			return ResponseEntity.ok(Map.of("status", "aborted", "correlationId", correlationId));
		}
	}

	/**
	 * 声明式事务：提交双 Topic。
	 *
	 * @param correlationId 关联 ID
	 */
	@PostMapping("/declarative/commit/{correlationId}")
	public ResponseEntity<Map<String, String>> commitDeclarative(@PathVariable @NotBlank String correlationId) {
		transactionalDeclarativeDemoService.sendCommittedPairDeclarative(correlationId);
		return ResponseEntity.ok(Map.of(
			"status", "committed",
			"mode", "declarative-@Transactional",
			"correlationId", correlationId
		));
	}

	/**
	 * 声明式事务：发送后失败中止。
	 *
	 * @param correlationId 关联 ID
	 */
	@PostMapping("/declarative/rollback/{correlationId}")
	public ResponseEntity<Map<String, String>> rollbackDeclarative(@PathVariable @NotBlank String correlationId) {
		try {
			transactionalDeclarativeDemoService.sendFailingPairDeclarative(correlationId);
			return ResponseEntity.internalServerError().body(Map.of("status", "unexpected-success"));
		}
		catch (IllegalStateException ex) {
			return ResponseEntity.ok(Map.of(
				"status", "aborted",
				"mode", "declarative-@Transactional",
				"correlationId", correlationId
			));
		}
	}

	/**
	 * 链式事务：写库并发送 Kafka（提交）。
	 *
	 * @param correlationId 关联 ID（唯一）
	 */
	@PostMapping("/db-kafka/commit/{correlationId}")
	public ResponseEntity<Map<String, String>> commitDbAndKafka(@PathVariable @NotBlank String correlationId) {
		dbAndKafkaChainedDemoService.saveAndPublish(correlationId);
		return ResponseEntity.ok(Map.of(
			"status", "committed",
			"mode", "chained-jpa-kafka",
			"correlationId", correlationId
		));
	}

	/**
	 * 链式事务：写库并发送后失败，整体回滚。
	 *
	 * @param correlationId 关联 ID
	 */
	@PostMapping("/db-kafka/rollback/{correlationId}")
	public ResponseEntity<Map<String, String>> rollbackDbAndKafka(@PathVariable @NotBlank String correlationId) {
		try {
			dbAndKafkaChainedDemoService.saveAndPublishFailing(correlationId);
			return ResponseEntity.internalServerError().body(Map.of("status", "unexpected-success"));
		}
		catch (IllegalStateException ex) {
			return ResponseEntity.ok(Map.of(
				"status", "aborted",
				"mode", "chained-jpa-kafka",
				"correlationId", correlationId
			));
		}
	}
}
