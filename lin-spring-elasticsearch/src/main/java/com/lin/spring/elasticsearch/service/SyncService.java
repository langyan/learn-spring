package com.lin.spring.elasticsearch.service;

import com.lin.spring.elasticsearch.entity.Product;
import com.lin.spring.elasticsearch.entity.ProductDocument;
import com.lin.spring.elasticsearch.entity.SyncLog;
import com.lin.spring.elasticsearch.entity.SyncLog.SyncOperation;
import com.lin.spring.elasticsearch.entity.SyncLog.SyncStatus;
import com.lin.spring.elasticsearch.repository.ProductElasticsearchRepository;
import com.lin.spring.elasticsearch.repository.ProductJpaRepository;
import com.lin.spring.elasticsearch.repository.SyncLogRepository;
import com.lin.spring.elasticsearch.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SyncService {

    @Autowired
    private ProductElasticsearchRepository esRepository;

    @Autowired
    private SyncLogRepository syncLogRepository;

    @Autowired
    private ProductMapper mapper;

    @Autowired
    private ProductJpaRepository productRepository;

    @Async
    public void syncToElasticsearch(Product product, SyncOperation operation) {
        SyncLog syncLog = new SyncLog();
        syncLog.setProductId(product.getId());
        syncLog.setOperation(operation);
        syncLog.setStatus(SyncStatus.PENDING);
        syncLog.setRetryCount(0);

        try {
            ProductDocument doc = mapper.toDocument(product);

            switch (operation) {
                case CREATE, UPDATE -> esRepository.save(doc);
                case DELETE -> esRepository.deleteById(doc.getId());
            }

            syncLog.setStatus(SyncStatus.SUCCESS);
        } catch (Exception e) {
            syncLog.setStatus(SyncStatus.FAILED);
            syncLog.setErrorMessage(e.getMessage());
        }

        syncLogRepository.save(syncLog);
    }

    public List<SyncLog> getAllSyncLogs() {
        return syncLogRepository.findAll();
    }

    public List<SyncLog> getFailedSyncs() {
        return syncLogRepository.findByStatus(SyncStatus.FAILED);
    }

    public void retrySync(Long syncLogId) {
        SyncLog syncLog = syncLogRepository.findById(syncLogId)
                .orElseThrow(() -> new RuntimeException("Sync log not found"));

        if (syncLog.getRetryCount() >= 3) {
            throw new RuntimeException("Max retry limit reached");
        }

        syncLog.setRetryCount(syncLog.getRetryCount() + 1);
        syncLog.setStatus(SyncStatus.PENDING);
        syncLog.setErrorMessage(null);
        syncLogRepository.save(syncLog);

        // Trigger async retry
        Product product = productRepository.findById(syncLog.getProductId())
                .orElseThrow(() -> new RuntimeException("Product not found"));
        retryAsync(product, syncLog.getOperation());
    }

    @Async
    public void retryAsync(Product product, SyncOperation operation) {
        syncToElasticsearch(product, operation);
    }
}
