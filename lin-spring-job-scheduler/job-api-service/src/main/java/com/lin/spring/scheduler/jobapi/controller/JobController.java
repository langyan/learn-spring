package com.lin.spring.scheduler.jobapi.controller;

import com.lin.spring.scheduler.jobapi.dto.CreateJobRequest;
import com.lin.spring.scheduler.jobapi.dto.JobResponse;
import com.lin.spring.scheduler.jobapi.dto.JobStatusResponse;
import com.lin.spring.scheduler.jobapi.dto.UpdateJobRequest;
import com.lin.spring.scheduler.jobapi.service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 作业 RESTful API（文章定义的六个端点）。
 */
@RestController
@RequestMapping
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /** POST /v1/api/jobs —— 创建新作业 */
    @PostMapping("/v1/api/jobs")
    public ResponseEntity<JobResponse> create(@Valid @RequestBody CreateJobRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobService.create(request));
    }

    /** GET /v1/api/job/{jobId} —— 获取作业详情 */
    @GetMapping("/v1/api/job/{jobId}")
    public JobResponse get(@PathVariable String jobId) {
        return jobService.get(jobId);
    }

    /** GET /v1/api/job/{jobId}/status —— 获取当前执行状态（含最近一次执行信息） */
    @GetMapping("/v1/api/job/{jobId}/status")
    public JobStatusResponse status(@PathVariable String jobId) {
        return jobService.status(jobId);
    }

    /** PUT /v1/api/job/{jobId} —— 更新现有作业（仅 PENDING） */
    @PutMapping("/v1/api/job/{jobId}")
    public JobResponse update(@PathVariable String jobId, @Valid @RequestBody UpdateJobRequest request) {
        return jobService.update(jobId, request);
    }

    /** POST /v1/jobs/{jobId}/cancel —— 取消预约或排队作业（Redis TTL 30s 标记） */
    @PostMapping("/v1/jobs/{jobId}/cancel")
    public JobResponse cancel(@PathVariable String jobId) {
        return jobService.cancel(jobId);
    }

    /** POST /v1/jobs/{jobId}/runNow —— 立即执行任务，绕过调度时间直推 Kafka */
    @PostMapping("/v1/jobs/{jobId}/runNow")
    public JobResponse runNow(@PathVariable String jobId) {
        return jobService.runNow(jobId);
    }
}
