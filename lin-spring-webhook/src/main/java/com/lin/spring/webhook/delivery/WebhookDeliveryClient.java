package com.lin.spring.webhook.delivery;

import com.lin.spring.webhook.domain.FailureType;
import com.lin.spring.webhook.dto.DeliveryResult;
import com.lin.spring.webhook.security.PinnedTarget;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * 真正发起 HTTP POST 的投递客户端。
 *
 * <p>URI 用原始主机名构造（保证 Host 头与 TLS SNI 正确），但底层连接由
 * {@code DnsPinningResolver} 强制指向校验阶段拿到的那个 IP，绝不二次解析 DNS。</p>
 */
@Slf4j
@Component
public class WebhookDeliveryClient {

	private final CloseableHttpClient httpClient;
	private final SignatureSigner signatureSigner;

	public WebhookDeliveryClient(CloseableHttpClient httpClient, SignatureSigner signatureSigner) {
		this.httpClient = httpClient;
		this.signatureSigner = signatureSigner;
	}

	/**
	 * 向已固定的目标投递事件。
	 *
	 * @param target    经过 SSRF 校验的目标
	 * @param payload   业务载荷
	 * @param eventId   事件 ID（幂等键，随请求头一并带上）
	 * @param eventType 事件类型
	 * @param secret    端点签名密钥
	 * @return 投递结果
	 */
	public DeliveryResult deliver(PinnedTarget target, String payload, String eventId,
								  String eventType, String secret) {
		long start = System.nanoTime();
		try {
			URI uri = new URI(target.scheme(), null, target.originalHost(), target.port(),
				target.path(), target.query(), null);
			HttpPost post = new HttpPost(uri);
			post.setHeader("Content-Type", "application/json");
			post.setHeader("User-Agent", "LinSpring-Webhook/1.0");
			post.setHeader("X-Webhook-Id", eventId);
			post.setHeader("X-Webhook-Event-Type", String.valueOf(eventType));
			if (secret != null && !secret.isBlank()) {
				post.setHeader("X-Webhook-Signature", signatureSigner.sign(payload, secret));
			}
			post.setEntity(new StringEntity(payload == null ? "{}" : payload, ContentType.APPLICATION_JSON));

			try (CloseableHttpResponse response = httpClient.execute(post)) {
				int status = response.getCode();
				EntityUtils.consumeQuietly(response.getEntity());
				long latencyMs = elapsedMillis(start);
				if (status >= 200 && status < 300) {
					return DeliveryResult.ok(status, latencyMs);
				}
				FailureType failureType = classify(status);
				return DeliveryResult.failure(status, latencyMs, "HTTP " + status, failureType);
			}
		} catch (IOException | URISyntaxException ex) {
			long latencyMs = elapsedMillis(start);
			log.debug("投递异常 host={} eventId={} error={}", target.originalHost(), eventId, ex.toString());
			return DeliveryResult.failure(0, latencyMs, ex.getClass().getSimpleName() + ": " + ex.getMessage(),
				FailureType.RETRYABLE);
		}
	}

	/**
	 * 依据状态码判定是否值得重试。
	 * 5xx / 408 / 429 视为可重试；其余 4xx 重试没有意义。
	 */
	private FailureType classify(int status) {
		if (status == 408 || status == 429) {
			return FailureType.RETRYABLE;
		}
		if (status >= 500) {
			return FailureType.RETRYABLE;
		}
		return FailureType.NON_RETRYABLE;
	}

	private long elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}
}
