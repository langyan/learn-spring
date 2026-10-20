package com.lin.spring.webhook.repository;

import com.lin.spring.webhook.domain.EndpointStatus;
import com.lin.spring.webhook.entity.WebhookEndpointEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 端点仓储。
 */
public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpointEntity, Long> {

	Optional<WebhookEndpointEntity> findByEndpointId(String endpointId);

	List<WebhookEndpointEntity> findByMerchantId(String merchantId);

	List<WebhookEndpointEntity> findByMerchantIdAndStatus(String merchantId, EndpointStatus status);
}
