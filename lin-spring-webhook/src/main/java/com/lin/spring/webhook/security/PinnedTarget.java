package com.lin.spring.webhook.security;

import java.net.InetAddress;

/**
 * 经过 SSRF 校验并固定下来的投递目标。
 *
 * <p>{@code pinnedIp} 是校验阶段解析得到的公网 IP，后续 HTTP 客户端必须直连它，
 * 而 {@code originalHost} 只用于 Host 头与 TLS SNI。</p>
 */
public record PinnedTarget(
	String originalHost,
	int port,
	InetAddress pinnedIp,
	String scheme,
	String path,
	String query
) {
}
