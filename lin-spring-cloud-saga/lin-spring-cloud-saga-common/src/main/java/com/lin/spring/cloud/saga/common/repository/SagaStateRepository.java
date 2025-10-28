package com.lin.spring.cloud.saga.common.repository;

import com.lin.spring.cloud.saga.common.entity.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SagaStateRepository extends JpaRepository<SagaState, Long> {
    Optional<SagaState> findBySagaId(String sagaId);
    boolean existsBySagaId(String sagaId);
}