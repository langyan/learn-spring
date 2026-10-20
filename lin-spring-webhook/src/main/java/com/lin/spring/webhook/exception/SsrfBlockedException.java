package com.lin.spring.webhook.exception;

/**
 * URL 未通过 SSRF 校验（含 DNS Rebinding 场景）时抛出。
 */
public class SsrfBlockedException extends RuntimeException {

	private final String url;

	public SsrfBlockedException(String url, String reason) {
		super("Webhook URL 被安全策略拦截: url=" + url + ", reason=" + reason);
		this.url = url;
	}

	public String getUrl() {
		return url;
	}
}
