package com.lin.spring.notification.provider;

/**
 * Provider 失败分类，决定是否进入重试队列。
 */
public enum FailureType {
	RETRYABLE,
	NON_RETRYABLE
}
