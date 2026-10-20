package com.lin.spring.webhook.security;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * 主机名解析抽象。抽成接口是为了让测试可以注入「第一次返回公网 IP、第二次返回内网 IP」的
 * 伪造解析器，从而复现 DNS Rebinding 攻击。
 */
public interface HostResolver {

	/**
	 * 解析主机名。
	 *
	 * @param host 主机名
	 * @return 解析到的全部地址
	 * @throws UnknownHostException 解析失败
	 */
	List<InetAddress> resolve(String host) throws UnknownHostException;
}
