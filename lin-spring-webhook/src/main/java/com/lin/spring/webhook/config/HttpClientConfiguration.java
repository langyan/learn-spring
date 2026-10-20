package com.lin.spring.webhook.config;

import com.lin.spring.webhook.security.DnsPinningResolver;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 投递用的 HTTP 客户端：固定 IP 的 DNS 解析器 + 严格超时 + 关闭自动重定向与自动重试。
 */
@Configuration
public class HttpClientConfiguration {

	@Bean(destroyMethod = "close")
	public PoolingHttpClientConnectionManager webhookConnectionManager(
		DnsPinningResolver dnsPinningResolver,
		WebhookProperties properties
	) {
		PoolingHttpClientConnectionManager manager = PoolingHttpClientConnectionManagerBuilder.create()
			.setDnsResolver(dnsPinningResolver)
			.setMaxConnTotal(properties.getDelivery().getMaxConnections())
			.setMaxConnPerRoute(properties.getDelivery().getMaxConnectionsPerRoute())
			.setDefaultConnectionConfig(ConnectionConfig.custom()
				.setConnectTimeout(Timeout.of(properties.getDelivery().getConnectTimeoutMs(), TimeUnit.MILLISECONDS))
				.setSocketTimeout(Timeout.of(properties.getDelivery().getResponseTimeoutMs(), TimeUnit.MILLISECONDS))
				.build())
			.setValidateAfterInactivity(TimeValue.ofSeconds(5))
			.build();
		return manager;
	}

	@Bean(destroyMethod = "close")
	public CloseableHttpClient webhookHttpClient(
		PoolingHttpClientConnectionManager connectionManager,
		WebhookProperties properties
	) {
		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectTimeout(Timeout.of(properties.getDelivery().getConnectTimeoutMs(), TimeUnit.MILLISECONDS))
			.setResponseTimeout(Timeout.of(properties.getDelivery().getResponseTimeoutMs(), TimeUnit.MILLISECONDS))
			.build();
		return HttpClients.custom()
			.setConnectionManager(connectionManager)
			.setDefaultRequestConfig(requestConfig)
			.disableAutomaticRetries()
			.disableRedirectHandling()
			.build();
	}
}
