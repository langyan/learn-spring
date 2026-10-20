package com.lin.spring.webhook.security;

import com.lin.spring.webhook.config.WebhookProperties;
import com.lin.spring.webhook.exception.SsrfBlockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * SSRF 防护：商户提交的 URL 不能指向内网或任何保留网段。
 */
class SsrfGuardTest {

	private SsrfGuard guard;
	private PinRegistry pinRegistry;
	private StubHostResolver resolver;

	@BeforeEach
	void setUp() {
		WebhookProperties properties = new WebhookProperties();
		pinRegistry = new PinRegistry(properties);
		resolver = new StubHostResolver();
		guard = new SsrfGuard(new EndpointUrlValidator(properties), resolver, pinRegistry, properties);
	}

	@Test
	void 公网地址应当放行并固定IP() {
		resolver.respond("merchant.example.com", "93.184.216.34");

		PinnedTarget target = guard.validateAndPin("https://merchant.example.com/webhook");

		assertEquals("merchant.example.com", target.originalHost());
		assertEquals(443, target.port());
		assertEquals("93.184.216.34", target.pinnedIp().getHostAddress());
		assertEquals("/webhook", target.path());
		assertNotNull(pinRegistry.pinnedAddress("merchant.example.com").orElse(null));
	}

	@Test
	void 端口白名单之外的地址应被拦截() {
		WebhookProperties properties = new WebhookProperties();
		properties.getSsrf().setAllowedPorts(List.of(80, 443));
		SsrfGuard strictGuard = new SsrfGuard(new EndpointUrlValidator(properties), resolver,
			new PinRegistry(properties), properties);
		resolver.respond("merchant.example.com", "93.184.216.34");

		assertThrows(SsrfBlockedException.class,
			() -> strictGuard.validateAndPin("http://merchant.example.com:22/webhook"));
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"http://127.0.0.1:8080/webhook",           // loopback
		"http://localhost:8080/webhook",           // localhost（未加白名单时同样拦截）
		"http://10.0.0.5/webhook",                 // RFC1918 A
		"http://172.16.3.4/webhook",               // RFC1918 B
		"http://192.168.1.9/webhook",              // RFC1918 C
		"http://169.254.169.254/latest/meta-data", // 云元数据服务
		"http://100.64.7.1/webhook",               // CGNAT
		"http://0.0.0.0/webhook",                  // 未指定地址
		"http://[::1]/webhook",                    // IPv6 loopback
		"http://[fd00::1]/webhook",                // IPv6 unique local
		"http://[fe80::1]/webhook"                 // IPv6 link-local
	})
	void 私有与保留网段一律拦截(String url) {
		SsrfBlockedException ex = assertThrows(SsrfBlockedException.class, () -> guard.validateAndPin(url));
		assertEquals(url, ex.getUrl());
	}

	@ParameterizedTest
	@ValueSource(strings = {
		"file:///etc/passwd",
		"ftp://merchant.example.com/webhook",
		"http:///webhook"
	})
	void 非http或非法URL一律拦截(String url) {
		assertThrows(SsrfBlockedException.class, () -> guard.validateAndPin(url));
	}

	/** 只在测试里使用的解析器：显式指定解析结果，避免依赖真实 DNS。 */
	private static class StubHostResolver implements HostResolver {

		private String host;
		private String address;

		void respond(String host, String address) {
			this.host = host;
			this.address = address;
		}

		@Override
		public List<InetAddress> resolve(String lookupHost) throws UnknownHostException {
			if (host != null && host.equals(lookupHost)) {
				return List.of(InetAddress.getByName(address));
			}
			// 其余情况按字面量解析（不发起网络查询）
			return List.of(InetAddress.getByName(lookupHost));
		}
	}
}
