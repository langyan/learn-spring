package com.lin.spring.webhook.delivery;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Stripe 风格的 Webhook 签名：{@code t=<时间戳>,v1=<HMAC-SHA256(timestamp.payload)>}。
 *
 * <p>签名让商户能验证请求确实来自我们，也顺带参与接收端的重放防护。</p>
 */
@Component
public class SignatureSigner {

	private static final String HMAC_ALGORITHM = "HmacSHA256";

	/**
	 * 生成签名头。
	 *
	 * @param payload 原始载荷
	 * @param secret  端点密钥
	 * @return 签名头内容
	 */
	public String sign(String payload, String secret) {
		long timestamp = System.currentTimeMillis() / 1000;
		return sign(payload, secret, timestamp);
	}

	public String sign(String payload, String secret, long timestampSeconds) {
		String signedPayload = timestampSeconds + "." + (payload == null ? "" : payload);
		try {
			Mac mac = Mac.getInstance(HMAC_ALGORITHM);
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
			byte[] digest = mac.doFinal(signedPayload.getBytes(StandardCharsets.UTF_8));
			return "t=" + timestampSeconds + ",v1=" + toHex(digest);
		} catch (Exception ex) {
			throw new IllegalStateException("生成 Webhook 签名失败", ex);
		}
	}

	/**
	 * 校验签名（Mock 接收端用来演示商户侧验证）。
	 *
	 * @param payload   原始载荷
	 * @param secret    端点密钥
	 * @param signature 请求头中的签名
	 * @return 是否匹配
	 */
	public boolean verify(String payload, String secret, String signature) {
		if (signature == null || !signature.contains("t=")) {
			return false;
		}
		try {
			long timestamp = Long.parseLong(signature.substring(signature.indexOf("t=") + 2, signature.indexOf(',')));
			return constantTimeEquals(sign(payload, secret, timestamp), signature);
		} catch (Exception ex) {
			return false;
		}
	}

	private boolean constantTimeEquals(String a, String b) {
		if (a.length() != b.length()) {
			return false;
		}
		int diff = 0;
		for (int i = 0; i < a.length(); i++) {
			diff |= a.charAt(i) ^ b.charAt(i);
		}
		return diff == 0;
	}

	private String toHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
		}
		return sb.toString();
	}
}
