package com.lin.spring.notification.provider;

/**
 * Provider 投递结果。
 */
public record ProviderResult(
	boolean success,
	FailureType failureType,
	String errorCode,
	String providerName
) {

	/**
	 * 投递成功。
	 */
	public static ProviderResult success(String providerName) {
		return new ProviderResult(true, null, null, providerName);
	}

	/**
	 * 可重试失败。
	 */
	public static ProviderResult failedRetryable(String providerName, String errorCode) {
		return new ProviderResult(false, FailureType.RETRYABLE, errorCode, providerName);
	}

	/**
	 * 不可重试失败。
	 */
	public static ProviderResult failedNonRetryable(String providerName, String errorCode) {
		return new ProviderResult(false, FailureType.NON_RETRYABLE, errorCode, providerName);
	}
}
