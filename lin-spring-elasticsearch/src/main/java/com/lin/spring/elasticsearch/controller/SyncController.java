package com.lin.spring.elasticsearch.controller;

import com.lin.spring.elasticsearch.entity.SyncLog;
import com.lin.spring.elasticsearch.service.SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    @Autowired
    private SyncService syncService;

    @GetMapping("/logs")
    public ResponseEntity<List<SyncLog>> getAllLogs() {
        return ResponseEntity.ok(syncService.getAllSyncLogs());
    }

    @GetMapping("/logs/failed")
    public ResponseEntity<List<SyncLog>> getFailedSyncs() {
        return ResponseEntity.ok(syncService.getFailedSyncs());
    }

    @PostMapping("/retry/{id}")
    public ResponseEntity<Void> retrySync(@PathVariable Long id) {
        try {
            syncService.retrySync(id);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
