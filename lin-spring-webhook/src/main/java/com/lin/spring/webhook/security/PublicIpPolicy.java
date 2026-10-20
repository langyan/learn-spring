package com.lin.spring.webhook.security;

import java.net.InetAddress;

/**
 * 公网可路由 IP 判定：命中任何私有 / 保留 / 特殊用途网段即拒绝。
 *
 * <p>这是面试追问 3 里「严格检查该 IP 不属于任何私有或保留 IP 范围」的落地。</p>
 */
public final class PublicIpPolicy {

	private PublicIpPolicy() {
	}

	/**
	 * 判断地址是否为允许投递的公网可路由地址。
	 *
	 * @param address 目标地址
	 * @return true 表示可以安全投递
	 */
	public static boolean isPubliclyRoutable(InetAddress address) {
		if (address == null) {
			return false;
		}
		if (address.isAnyLocalAddress()
			|| address.isLoopbackAddress()
			|| address.isLinkLocalAddress()
			|| address.isMulticastAddress()
			|| address.isSiteLocalAddress()) {
			return false;
		}
		byte[] b = address.getAddress();
		if (b.length == 4) {
			return isPublicIpv4(b);
		}
		if (b.length == 16) {
			return isPublicIpv6(b);
		}
		return false;
	}

	private static boolean isPublicIpv4(byte[] b) {
		int o0 = b[0] & 0xFF;
		int o1 = b[1] & 0xFF;
		int o2 = b[2] & 0xFF;

		if (o0 == 0) {
			return false;                                   // 0.0.0.0/8
		}
		if (o0 == 10) {
			return false;                                   // 10.0.0.0/8
		}
		if (o0 == 127) {
			return false;                                   // 127.0.0.0/8 loopback
		}
		if (o0 == 100 && (o1 & 0xC0) == 0x40) {
			return false;                                   // 100.64.0.0/10 CGNAT
		}
		if (o0 == 169 && o1 == 254) {
			return false;                                   // 169.254.0.0/16 link-local / cloud metadata
		}
		if (o0 == 172 && o1 >= 16 && o1 <= 31) {
			return false;                                   // 172.16.0.0/12
		}
		if (o0 == 192 && o1 == 0 && o2 == 0) {
			return false;                                   // 192.0.0.0/24
		}
		if (o0 == 192 && o1 == 0 && o2 == 2) {
			return false;                                   // 192.0.2.0/24 TEST-NET-1
		}
		if (o0 == 192 && o1 == 88 && o2 == 99) {
			return false;                                   // 192.88.99.0/24 6to4 relay
		}
		if (o0 == 192 && o1 == 168) {
			return false;                                   // 192.168.0.0/16
		}
		if (o0 == 198 && (o1 == 18 || o1 == 19)) {
			return false;                                   // 198.18.0.0/15 benchmarking
		}
		if (o0 == 198 && o1 == 51 && o2 == 100) {
			return false;                                   // 198.51.100.0/24 TEST-NET-2
		}
		if (o0 == 203 && o1 == 0 && o2 == 113) {
			return false;                                   // 203.0.113.0/24 TEST-NET-3
		}
		if ((o0 & 0xF0) == 224) {
			return false;                                   // 224.0.0.0/4 multicast
		}
		if ((o0 & 0xF0) == 240) {
			return false;                                   // 240.0.0.0/4 reserved（含 255.255.255.255）
		}
		return true;
	}

	private static boolean isPublicIpv6(byte[] b) {
		int b0 = b[0] & 0xFF;
		int b1 = b[1] & 0xFF;

		boolean allZeroExceptLast4 = true;
		for (int i = 0; i < 12; i++) {
			if (b[i] != 0) {
				allZeroExceptLast4 = false;
				break;
			}
		}
		if (allZeroExceptLast4) {
			// ::ffff:a.b.c.d（IPv4-mapped）与 ::a.b.c.d（IPv4-compatible）必须按内嵌 IPv4 判定
			byte[] embedded = new byte[] {b[12], b[13], b[14], b[15]};
			return isPublicIpv4(embedded);
		}

		if ((b0 & 0xFE) == 0xFC) {
			return false;                                   // fc00::/7 unique local
		}
		if (b0 == 0xFE && (b1 & 0xC0) == 0x80) {
			return false;                                   // fe80::/10 link-local
		}
		if (b0 == 0xFF) {
			return false;                                   // ff00::/8 multicast
		}
		if (b0 == 0x20 && b1 == 0x01 && (b[2] & 0xFF) == 0x0D && (b[3] & 0xFF) == 0xB8) {
			return false;                                   // 2001:db8::/32 documentation
		}
		if (b0 == 0x20 && b1 == 0x02) {
			return false;                                   // 2002::/16 6to4
		}
		if (b0 == 0x00 && b1 == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B) {
			return false;                                   // 64:ff9b::/96 NAT64
		}
		return true;
	}
}
