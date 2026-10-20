package com.lin.spring.webhook.config;

import com.lin.spring.webhook.dto.RegisterEndpointRequest;
import com.lin.spring.webhook.service.EndpointRegistryService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.util.List;

/**
 * 预置演示数据：五个商户，分别对应健康 / 500 / 挂起 30 秒 / 慢速 / 幂等五种接收端。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.webhook.demo-data.enabled", havingValue = "true", matchIfMissing = true)
public class DataInitializer {

	private final EndpointRegistryService endpointRegistry;

	@Value("${server.port:8096}")
	private int port;

	public DataInitializer(EndpointRegistryService endpointRegistry) {
		this.endpointRegistry = endpointRegistry;
	}

	@PostConstruct
	public void init() {
		register("mch_healthy", "ok");
		register("mch_broken", "fail500");
		register("mch_slowpoke", "hang30s");
		register("mch_laggy", "slow");
		register("mch_accounting", "idempotent");
		log.info("演示数据已就绪：mch_healthy / mch_broken / mch_slowpoke / mch_laggy / mch_accounting");
	}

	private void register(String merchantId, String mode) {
		RegisterEndpointRequest request = new RegisterEndpointRequest();
		request.setUrl("http://localhost:" + port + "/api/mock/receiver/" + mode);
		request.setSecret("whsec_" + merchantId);
		request.setEventTypes(List.of());
		endpointRegistry.register(merchantId, request);
	}
}
