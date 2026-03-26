package com.lin.spring.elasticsearch.controller;

import com.lin.spring.elasticsearch.entity.Outbox;
import com.lin.spring.elasticsearch.entity.OutboxStatus;
import com.lin.spring.elasticsearch.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
public class OutboxController {

    private final OutboxRepository outboxRepository;

    @GetMapping("/pending")
    public ResponseEntity<List<Outbox>> getPendingEvents() {
        List<Outbox> pendingEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(
            OutboxStatus.PENDING);
        return ResponseEntity.ok(pendingEvents);
    }

    @GetMapping("/failed")
    public ResponseEntity<List<Outbox>> getFailedEvents() {
        List<Outbox> failedEvents = outboxRepository.findByStatusOrderByCreatedAtAsc(
            OutboxStatus.FAILED);
        return ResponseEntity.ok(failedEvents);
    }

    @PostMapping("/retry/{id}")
    @Transactional
    public ResponseEntity<Outbox> retryEvent(@PathVariable Long id) {
        return outboxRepository.findById(id)
            .map(outbox -> {
                outbox.setStatus(OutboxStatus.PENDING);
                outbox.setRetryCount(outbox.getRetryCount() + 1);
                outbox.setErrorMessage(null);
                Outbox updated = outboxRepository.save(outbox);
                log.info("Retried outbox event: id={}", id);
                return ResponseEntity.ok(updated);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Void> deleteEvent(@PathVariable Long id) {
        if (outboxRepository.existsById(id)) {
            outboxRepository.deleteById(id);
            log.info("Deleted outbox event: id={}", id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/cleanup")
    @Transactional
    public ResponseEntity<Void> cleanupCompletedEvents(
            @RequestParam(defaultValue = "7") int daysOld) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysOld);
        List<Outbox> completedEvents = outboxRepository
            .findByStatusAndProcessedAtBeforeOrderByCreatedAtDesc(
                OutboxStatus.PUBLISHED, cutoffDate);

        completedEvents.forEach(event -> log.info("Cleaning up completed outbox event: id={}",
            event.getId()));
        outboxRepository.deleteAll(completedEvents);

        return ResponseEntity.noContent().build();
    }
}
