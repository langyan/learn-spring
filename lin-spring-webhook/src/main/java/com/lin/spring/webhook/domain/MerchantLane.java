package com.lin.spring.webhook.domain;

/**
 * 商户泳道（对齐面试追问 2 的租户隔离）。
 *
 * <ul>
 *   <li>MAIN：健康商户，走主投递队列</li>
 *   <li>SLOW：目的地已跳闸，事件临时导入专门的延迟队列</li>
 *   <li>QUARANTINE：目的地离线超时，直接取消，不再消耗任何投递资源</li>
 * </ul>
 */
public enum MerchantLane {
	MAIN,
	SLOW,
	QUARANTINE
}
