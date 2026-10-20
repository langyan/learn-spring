package com.lin.spring.webhook.security;

import com.lin.spring.webhook.exception.SsrfBlockedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.List;

/**
 * SSRF 防护组合入口：URL 校验 → 解析一次 → 逐个校验公网 → 固定 IP。
 */
@Slf4j
@Component
public class SsrfGuard {

	private final EndpointUrlValidator urlValidator;
	private final HostResolver hostResolver;
	private final PinRegistry pinRegistry;

	public SsrfGuard(EndpointUrlValidator urlValidator, HostResolver hostResolver, PinRegistry pinRegistry,
					 com.lin.spring.webhook.config.WebhookProperties properties) {
		this.urlValidator = urlValidator;
		this.hostResolver = hostResolver;
		this.pinRegistry = pinRegistry;
		this.properties = properties;
	}

	private final com.lin.spring.webhook.config.WebhookProperties properties;

	/**
	 * 校验 URL 并固定目标 IP。
	 *
	 * @param rawUrl 商户端点 URL
	 * @return 只能直连到已校验 IP 的投递目标
	 * @throws SsrfBlockedException URL 非法或解析结果命中私有/保留网段
	 */
	public PinnedTarget validateAndPin(String rawUrl) {
		EndpointUrlValidator.ParsedUrl parsed = urlValidator.parse(rawUrl);
		String host = parsed.host();
		String lookupHost = host.startsWith("[") && host.endsWith("]")
			? host.substring(1, host.length() - 1)
			: host;

		List<InetAddress> addresses;
		try {
			// 全流程只在这里解析一次 DNS
			addresses = hostResolver.resolve(lookupHost);
		} catch (Exception ex) {
			throw new SsrfBlockedException(rawUrl, "DNS 解析失败: " + ex.getMessage());
		}
		if (addresses == null || addresses.isEmpty()) {
			throw new SsrfBlockedException(rawUrl, "DNS 未返回任何地址");
		}
		boolean hostAllowlisted = properties.getSsrf().getAllowedHosts().stream()
			.anyMatch(allowed -> allowed.equalsIgnoreCase(host));
		if (!hostAllowlisted) {
			for (InetAddress address : addresses) {
				if (!PublicIpPolicy.isPubliclyRoutable(address)) {
					log.warn("SSRF 拦截: url={} host={} 解析到非公网地址 {}", rawUrl, host, address.getHostAddress());
					throw new SsrfBlockedException(rawUrl,
						host + " 解析到非公网地址 " + address.getHostAddress());
				}
			}
		}

		InetAddress pinned = addresses.get(0);
		// 固定主机名与 IP 的绑定关系，后续 HTTP 客户端只能用这个 IP
		pinRegistry.pin(lookupHost, pinned);
		return new PinnedTarget(lookupHost, parsed.port(), pinned, parsed.scheme(), parsed.path(), parsed.query());
	}
}
