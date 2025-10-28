package com.lin.spring.cloud.saga.orchestrator.controller;

import com.lin.spring.cloud.saga.common.dto.SagaRequest;
import com.lin.spring.cloud.saga.common.dto.SagaResponse;
import com.lin.spring.cloud.saga.orchestrator.service.SagaOrchestratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/saga")
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestratorController {

    private final SagaOrchestratorService sagaOrchestratorService;

    @PostMapping("/execute")
    public ResponseEntity<SagaResponse> executeSaga(@Valid @RequestBody SagaRequest request) {
        log.info("Received execute saga request: {}", request);
        try {
            SagaResponse response = sagaOrchestratorService.executeSaga(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error executing saga: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping("/{sagaId}")
    public ResponseEntity<SagaResponse> getSagaStatus(@PathVariable String sagaId) {
        log.info("Received get saga status request: {}", sagaId);
        try {
            SagaResponse response = sagaOrchestratorService.getSagaStatus(sagaId);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting saga status: {}", e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }
}