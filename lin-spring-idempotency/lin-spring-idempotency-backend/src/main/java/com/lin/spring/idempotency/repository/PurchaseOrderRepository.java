package com.lin.spring.idempotency.repository;

import com.lin.spring.idempotency.entity.PurchaseOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, Long> {

	Optional<PurchaseOrder> findByOrderNo(String orderNo);
}
