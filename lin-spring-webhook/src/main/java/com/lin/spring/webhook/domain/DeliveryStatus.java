package com.lin.spring.webhook.domain;

/**
 * 事件的整体投递状态。
 */
public enum DeliveryStatus {
	/** 已接收，尚未产生投递结果 */
	PENDING,
	/** 投递失败但仍在退避重试窗口内 */
	RETRYING,
	/** 收到 2xx，投递成功 */
	DELIVERED,
	/** 彻底失败（不可重试 / 重试超限），进入死信队列 */
	FAILED,
	/** 目的地长时间离线，被主动取消，避免消耗主投递池 */
	CANCELLED
}
