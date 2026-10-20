package com.lin.spring.webhook.security;

import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 默认的主机解析器，走 JVM 系统 DNS。
 *
 * <p>注意：整个系统里只有这一处允许真正发起 DNS 查询。</p>
 */
@Component
public class SystemHostResolver implements HostResolver {

	@Override
	public List<InetAddress> resolve(String host) throws UnknownHostException {
		return List.of(InetAddress.getAllByName(host));
	}
}
