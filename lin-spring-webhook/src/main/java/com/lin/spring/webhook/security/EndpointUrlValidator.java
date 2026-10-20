package com.lin.spring.webhook.security;

import com.lin.spring.webhook.config.WebhookProperties;
import com.lin.spring.webhook.exception.SsrfBlockedException;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

/**
 * URL 结构与端口白名单校验（SSRF 第一道闸门）。
 */
@Component
public class EndpointUrlValidator {

	private final WebhookProperties properties;

	public EndpointUrlValidator(WebhookProperties properties) {
		this.properties = properties;
	}

	public record ParsedUrl(String scheme, String host, int port, String path, String query) {

		public String origin() {
			return scheme + "://" + host + ":" + port;
		}
	}

	/**
	 * 校验 URL 结构并返回解析结果。
	 *
	 * @param rawUrl 商户提交的 URL
	 * @return 解析结果
	 */
	public ParsedUrl parse(String rawUrl) {
		if (rawUrl == null || rawUrl.isBlank()) {
			throw new SsrfBlockedException(String.valueOf(rawUrl), "URL 为空");
		}
		URI uri;
		try {
			uri = new URI(rawUrl.trim());
		} catch (URISyntaxException ex) {
			throw new SsrfBlockedException(rawUrl, "URL 格式非法");
		}
		String scheme = uri.getScheme();
		if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
			throw new SsrfBlockedException(rawUrl, "仅允许 http/https，实际为 " + scheme);
		}
		if (uri.getRawUserInfo() != null) {
			throw new SsrfBlockedException(rawUrl, "URL 中不允许携带用户名密码");
		}
		String host = uri.getHost();
		if (host == null || host.isBlank()) {
			throw new SsrfBlockedException(rawUrl, "无法解析出主机名");
		}
		int port = uri.getPort() > 0 ? uri.getPort() : ("https".equalsIgnoreCase(scheme) ? 443 : 80);
		List<Integer> allowed = properties.getSsrf().getAllowedPorts();
		if (allowed != null && !allowed.isEmpty() && !allowed.contains(port)) {
			throw new SsrfBlockedException(rawUrl, "端口 " + port + " 不在白名单 " + allowed + " 内");
		}
		String path = (uri.getRawPath() == null || uri.getRawPath().isBlank()) ? "/" : uri.getRawPath();
		return new ParsedUrl(scheme.toLowerCase(), host, port, path, uri.getRawQuery());
	}
}
