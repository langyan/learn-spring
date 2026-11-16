package com.lin.spring.events.ops;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 故障演练接口：注入投递失败 / 处理失败 / 持锁崩溃 / 重复投递。
 * <p>内存态开关，仅教学演示。</p>
 */
@RestController
@RequestMapping("/api/simulator")
@RequiredArgsConstructor
public class SimulatorController {

	private final FailureSimulator simulator;

	/**
	 * 当前开关状态。
	 */
	@GetMapping
	public ResponseEntity<FailureSimulator.Status> status() {
		return ResponseEntity.ok(simulator.status());
	}

	/**
	 * 接下来 N 次投递失败（模拟 broker 不可用）。
	 */
	@PostMapping("/fail-publish")
	public ResponseEntity<String> failPublish(@RequestParam(defaultValue = "1") int times) {
		simulator.failNextPublishes(times);
		return ResponseEntity.ok("接下来 " + times + " 次投递将失败");
	}

	/**
	 * 接下来 N 次消费处理失败（模拟下游业务故障）。
	 */
	@PostMapping("/fail-handler")
	public ResponseEntity<String> failHandler(@RequestParam(defaultValue = "1") int times) {
		simulator.failNextHandlers(times);
		return ResponseEntity.ok("接下来 " + times + " 次处理将失败");
	}

	/**
	 * 下一次派发申领后「崩溃」：记录停留在 PROCESSING，演示陈旧锁回收。
	 */
	@PostMapping("/crash-after-claim")
	public ResponseEntity<String> crashAfterClaim(@RequestParam(defaultValue = "true") boolean enabled) {
		simulator.setCrashAfterClaim(enabled);
		return ResponseEntity.ok(enabled
			? "下一次派发将模拟申领后崩溃（记录停留 PROCESSING）"
			: "已关闭申领后崩溃模拟");
	}

	/**
	 * 下一次投递重复两份（模拟 at-least-once 重复投递）。
	 */
	@PostMapping("/duplicate-next")
	public ResponseEntity<String> duplicateNext(@RequestParam(defaultValue = "true") boolean enabled) {
		simulator.setDuplicateNext(enabled);
		return ResponseEntity.ok(enabled
			? "下一次投递将重复投递 2 次（共 3 次送达）"
			: "已关闭重复投递模拟");
	}

	/**
	 * 全部复位。
	 */
	@DeleteMapping
	public ResponseEntity<String> reset() {
		simulator.reset();
		return ResponseEntity.ok("故障注入已全部复位");
	}
}
