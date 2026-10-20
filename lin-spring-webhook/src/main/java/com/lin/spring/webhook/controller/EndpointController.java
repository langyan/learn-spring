package com.lin.spring.webhook.controller;

import com.lin.spring.webhook.dto.RegisterEndpointRequest;
import com.lin.spring.webhook.entity.WebhookEndpointEntity;
import com.lin.spring.webhook.service.EndpointRegistryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 商户 Webhook 端点注册接口。注册时即做 SSRF 校验。
 */
@RestController
@RequestMapping("/api/merchants/{merchantId}/endpoints")
@RequiredArgsConstructor
public class EndpointController {

	private final EndpointRegistryService endpointRegistry;

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public WebhookEndpointEntity register(@PathVariable String merchantId,
										  @Valid @RequestBody RegisterEndpointRequest request) {
		return endpointRegistry.register(merchantId, request);
	}

	@GetMapping
	public List<WebhookEndpointEntity> list(@PathVariable String merchantId) {
		return endpointRegistry.allEndpoints(merchantId);
	}
}
