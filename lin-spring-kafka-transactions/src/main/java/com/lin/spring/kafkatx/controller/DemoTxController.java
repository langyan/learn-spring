package com.lin.spring.kafkatx.controller;

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
 */
@Validated
@RestController
@RequestMapping("/api/tx")
@RequiredArgsConstructor
public class DemoTxController {

	private final TransactionalDemoService transactionalDemoService;

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
}
