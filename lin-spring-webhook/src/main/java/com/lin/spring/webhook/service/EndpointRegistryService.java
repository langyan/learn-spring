package com.lin.spring.webhook.service;

import com.lin.spring.webhook.domain.EndpointStatus;
import com.lin.spring.webhook.dto.RegisterEndpointRequest;
import com.lin.spring.webhook.entity.MerchantEntity;
import com.lin.spring.webhook.entity.WebhookEndpointEntity;
import com.lin.spring.webhook.repository.MerchantRepository;
import com.lin.spring.webhook.repository.WebhookEndpointRepository;
import com.lin.spring.webhook.security.SsrfGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 商户端点注册与查询。
 *
 * <p>注册时就跑一遍 SSRF 校验，恶意 URL 根本进不了库。Endpoint 配置带短 TTL 本地缓存，
 * 避免每条事件都打一次 Postgres。</p>
 */
@Slf4j
@Service
public class EndpointRegistryService {

	private static final Duration CACHE_TTL = Duration.ofSeconds(30);

	private final WebhookEndpointRepository endpointRepository;
	private final MerchantRepository merchantRepository;
	private final SsrfGuard ssrfGuard;
	private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

	public EndpointRegistryService(WebhookEndpointRepository endpointRepository,
								   MerchantRepository merchantRepository,
								   SsrfGuard ssrfGuard) {
		this.endpointRepository = endpointRepository;
		this.merchantRepository = merchantRepository;
		this.ssrfGuard = ssrfGuard;
	}

	/**
	 * 注册端点。URL 未通过 SSRF 校验会直接抛异常。
	 */
	@Transactional
	public WebhookEndpointEntity register(String merchantId, RegisterEndpointRequest request) {
		ensureMerchant(merchantId);
		// 注册即校验，恶意地址无法入库
		ssrfGuard.validateAndPin(request.getUrl());

		WebhookEndpointEntity entity = new WebhookEndpointEntity();
		entity.setEndpointId("ep_" + UUID.randomUUID().toString().substring(0, 8));
		entity.setMerchantId(merchantId);
		entity.setUrl(request.getUrl());
		entity.setSecret(request.getSecret() == null || request.getSecret().isBlank()
			? "whsec_" + UUID.randomUUID().toString().substring(0, 12)
			: request.getSecret());
		entity.setStatus(EndpointStatus.NORMAL);
		entity.setEventTypes(request.getEventTypes() == null ? null : String.join(",", request.getEventTypes()));
		WebhookEndpointEntity saved = endpointRepository.save(entity);
		cache.remove(merchantId);
		log.info("注册端点 endpointId={} merchantId={} url={}", saved.getEndpointId(), merchantId, saved.getUrl());
		return saved;
	}

	/**
	 * 查询某商户所有可投递端点（带缓存）。
	 */
	@Transactional(readOnly = true)
	public List<WebhookEndpointEntity> activeEndpoints(String merchantId) {
		CacheEntry entry = cache.get(merchantId);
		if (entry != null && entry.expiresAt().isAfter(Instant.now())) {
			return entry.endpoints();
		}
		List<WebhookEndpointEntity> endpoints = endpointRepository
			.findByMerchantIdAndStatus(merchantId, EndpointStatus.NORMAL);
		cache.put(merchantId, new CacheEntry(endpoints, Instant.now().plus(CACHE_TTL)));
		return endpoints;
	}

	@Transactional(readOnly = true)
	public List<WebhookEndpointEntity> allEndpoints(String merchantId) {
		return endpointRepository.findByMerchantId(merchantId);
	}

	/**
	 * 修改端点状态（SSRF 拦截置 BLOCKED，收到 410 置 DISABLED）。
	 */
	@Transactional
	public void changeStatus(String endpointId, EndpointStatus status) {
		endpointRepository.findByEndpointId(endpointId).ifPresent(entity -> {
			entity.setStatus(status);
			endpointRepository.save(entity);
			cache.remove(entity.getMerchantId());
		});
	}

	@Transactional
	public void ensureMerchant(String merchantId) {
		merchantRepository.findByMerchantId(merchantId).orElseGet(() -> {
			MerchantEntity merchant = new MerchantEntity();
			merchant.setMerchantId(merchantId);
			merchant.setName(merchantId);
			return merchantRepository.save(merchant);
		});
	}

	private record CacheEntry(List<WebhookEndpointEntity> endpoints, Instant expiresAt) {
	}
}
