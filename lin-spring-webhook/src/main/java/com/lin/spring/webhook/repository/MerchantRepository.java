package com.lin.spring.webhook.repository;

import com.lin.spring.webhook.entity.MerchantEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 商户仓储。
 */
public interface MerchantRepository extends JpaRepository<MerchantEntity, Long> {

	Optional<MerchantEntity> findByMerchantId(String merchantId);
}
