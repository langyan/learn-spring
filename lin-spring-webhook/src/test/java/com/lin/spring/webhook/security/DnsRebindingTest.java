package com.lin.spring.webhook.security;

import com.lin.spring.webhook.config.WebhookProperties;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * DNS Rebinding：验证「只解析一次，并把该 IP 固定给 HTTP 客户端」。
 *
 * <p>攻击者在校验通过后把域名解析切回 127.0.0.1，但真正建立连接用的仍是校验通过的那个公网 IP。</p>
 */
class DnsRebindingTest {

	@Test
	void 攻击者切换解析结果后客户端仍使用校验通过的公网IP() throws Exception {
		WebhookProperties properties = new WebhookProperties();
		PinRegistry pinRegistry = new PinRegistry(properties);
		RebindingResolver resolver = new RebindingResolver();
		SsrfGuard guard = new SsrfGuard(new EndpointUrlValidator(properties), resolver, pinRegistry, properties);
		DnsPinningResolver clientResolver = new DnsPinningResolver(pinRegistry);

		// 1) 校验阶段：域名解析到公网地址
		PinnedTarget target = guard.validateAndPin("http://rebind.attacker.com/webhook");
		assertEquals("93.184.216.34", target.pinnedIp().getHostAddress());

		// 2) 攻击者把 TTL 极短的域名重新指向内网
		resolver.flipToPrivateAddress();
		assertEquals("127.0.0.1", resolver.peekAddress(), "攻击者已把解析结果切成内网地址");

		// 3) HTTP 客户端拿到的仍是校验通过的那个 IP，且全程只解析过一次
		InetAddress[] usedByClient = clientResolver.resolve("rebind.attacker.com");
		assertEquals(1, usedByClient.length);
		assertEquals("93.184.216.34", usedByClient[0].getHostAddress());
		assertEquals(1, resolver.resolveCount(), "HTTP 客户端绝不能发起第二次 DNS 解析");
	}

	@Test
	void 没有经过校验的主机名一律拒绝解析() {
		WebhookProperties properties = new WebhookProperties();
		DnsPinningResolver clientResolver = new DnsPinningResolver(new PinRegistry(properties));

		assertThrows(UnknownHostException.class, () -> clientResolver.resolve("never.validated.com"));
	}

	/** 第一次返回公网 IP，之后返回内网 IP 的伪造解析器。 */
	private static class RebindingResolver implements HostResolver {

		private int resolveCount = 0;
		private boolean flipped = false;

		@Override
		public List<InetAddress> resolve(String host) throws UnknownHostException {
			resolveCount++;
			String address = flipped ? "127.0.0.1" : "93.184.216.34";
			return List.of(InetAddress.getByName(address));
		}

		void flipToPrivateAddress() {
			this.flipped = true;
		}

		/** 只查看当前会返回什么地址，不计入解析次数。 */
		String peekAddress() {
			return flipped ? "127.0.0.1" : "93.184.216.34";
		}

		int resolveCount() {
			return resolveCount;
		}
	}
}
