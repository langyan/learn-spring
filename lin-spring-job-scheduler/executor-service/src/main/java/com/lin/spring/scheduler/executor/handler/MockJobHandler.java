package com.lin.spring.scheduler.executor.handler;

import com.lin.spring.scheduler.common.messaging.JobMessage;
import com.lin.spring.scheduler.common.util.JsonCodec;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 模拟作业处理器（压测用）：
 * payload JSON 形如 {"sleepMs":50, "failUntilAttempt":1}
 * - sleepMs：模拟业务耗时（默认 5ms，覆盖 10k/s 吞吐验证）
 * - failUntilAttempt：当 attemptNumber <= 该值时抛出瞬时错误，用于演示重试/DLQ 链路
 */
@Slf4j
@Component
public class MockJobHandler implements JobHandler {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record MockPayload(Integer sleepMs, Integer failUntilAttempt) {
        int effectiveSleepMs() {
            return sleepMs == null ? 5 : sleepMs;
        }

        int effectiveFailUntilAttempt() {
            return failUntilAttempt == null ? -1 : failUntilAttempt;
        }
    }

    @Override
    public void execute(JobMessage message) throws Exception {
        MockPayload payload = parse(message.payload());
        if (message.attemptNumber() <= payload.effectiveFailUntilAttempt()) {
            throw new IllegalStateException(
                    "Simulated transient failure, attempt=" + message.attemptNumber()
                            + " (fails until attempt " + payload.effectiveFailUntilAttempt() + ")");
        }
        if (payload.effectiveSleepMs() > 0) {
            Thread.sleep(payload.effectiveSleepMs());
        }
        log.debug("Executed job {} attempt {}", message.jobId(), message.attemptNumber());
    }

    private MockPayload parse(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) {
            return new MockPayload(5, -1);
        }
        try {
            MockPayload parsed = JsonCodec.read(payloadJson, MockPayload.class);
            return new MockPayload(parsed.effectiveSleepMs(), parsed.effectiveFailUntilAttempt());
        } catch (Exception e) {
            // 非法 payload 视为瞬时错误，交给重试链路
            throw new IllegalStateException("Invalid payload: " + payloadJson, e);
        }
    }
}
