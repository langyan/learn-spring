package com.lin.spring.scheduler.jobapi.controller;

import com.lin.spring.scheduler.jobapi.service.LoadTestService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 压测端点：
 * - POST /v1/api/jobs/loadtest?count=10000&spreadMs=1000&sleepMs=5
 * - GET  /v1/api/jobs/loadtest/stats
 * - POST /v1/api/jobs/loadtest/reset
 */
@RestController
@RequestMapping("/v1/api/jobs/loadtest")
@RequiredArgsConstructor
public class LoadTestController {

    private final LoadTestService loadTestService;

    @PostMapping
    public LoadTestService.SubmitResult submit(
            @RequestParam(defaultValue = "10000") int count,
            @RequestParam(defaultValue = "1000") long spreadMs,
            @RequestParam(defaultValue = "5") int sleepMs) {
        return loadTestService.submit(count, spreadMs, sleepMs);
    }

    @GetMapping("/stats")
    public LoadTestService.Stats stats() {
        return loadTestService.stats();
    }

    @PostMapping("/reset")
    public Map<String, Object> reset() {
        return Map.of("deletedJobs", loadTestService.reset());
    }
}
