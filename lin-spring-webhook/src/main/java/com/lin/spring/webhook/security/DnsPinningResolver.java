package com.lin.spring.webhook.security;

import org.apache.hc.client5.http.DnsResolver;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * 交给 Apache HttpClient 5 使用的 DNS 解析器。
 *
 * <p>它<b>从不</b>发起真正的 DNS 查询，只返回 {@link PinRegistry} 里已经过 SSRF 校验的 IP。
 * 这样无论 TTL 多短、无论攻击者如何切换解析结果，真正建立连接的那个 IP 永远是校验通过的那个。</p>
 */
@Component
public class DnsPinningResolver implements DnsResolver {

	private final PinRegistry pinRegistry;

	public DnsPinningResolver(PinRegistry pinRegistry) {
		this.pinRegistry = pinRegistry;
	}

	@Override
	public InetAddress[] resolve(String host) throws UnknownHostException {
		return pinRegistry.pinnedAddress(host)
			.map(address -> new InetAddress[] {address})
			.orElseThrow(() -> new UnknownHostException(
				"拒绝解析 " + host + "：没有经过 SSRF 校验的 IP pin，禁止发起二次 DNS 查询"));
	}

	@Override
	public String resolveCanonicalHostname(String host) throws UnknownHostException {
		// 不做任何 CNAME 追踪，避免绕过已固定的 IP
		return host;
	}
}
