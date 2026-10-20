package com.lin.spring.webhook.security;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「主机名 → 已校验 IP」的固定表。
 *
 * <p>HTTP 客户端只允许连接这张表里出现过的 IP。没有 pin 的主机名一律拒绝解析，
 * 从根本上杜绝「校验时是公网 IP、真正发请求时又解析一次拿到内网 IP」的 DNS Rebinding。</p>
 */
@Component
public class PinRegistry {

	private final ConcurrentHashMap<String, Pin> pins = new ConcurrentHashMap<>();
	private final Duration ttl;

	public PinRegistry(com.lin.spring.webhook.config.WebhookProperties properties) {
		this.ttl = Duration.ofSeconds(properties.getSsrf().getPinTtlSeconds());
	}

	private record Pin(InetAddress address, Instant expiresAt) {
	}

	/**
	 * 登记一个已通过校验的 IP（同一主机重复校验会刷新为最新结果）。
	 *
	 * @param host    主机名
	 * @param address 已校验为公网的地址
	 */
	public void pin(String host, InetAddress address) {
		pins.put(host, new Pin(address, Instant.now().plus(ttl)));
	}

	/**
	 * 取出仍未过期的 pin。
	 *
	 * @param host 主机名
	 * @return 已固定 IP，无则为空
	 */
	public Optional<InetAddress> pinnedAddress(String host) {
		Pin pin = pins.get(host);
		if (pin == null || pin.expiresAt().isBefore(Instant.now())) {
			return Optional.empty();
		}
		return Optional.of(pin.address());
	}

	public Map<String, String> snapshot() {
		Map<String, String> view = new java.util.LinkedHashMap<>();
		pins.forEach((host, pin) -> view.put(host, pin.address().getHostAddress()));
		return view;
	}
}
