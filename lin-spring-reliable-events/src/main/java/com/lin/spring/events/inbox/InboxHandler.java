package com.lin.spring.events.inbox;

import com.lin.spring.events.inbox.entity.InboxEvent;

/**
 * Inbox 业务处理器：按事件类型注册，处理成功正常返回，失败抛异常由重试编排接管。
 * <p>
 * 实现必须幂等或依赖 Inbox 幂等边界（eventId 唯一）保证副作用只发生一次。
 * </p>
 */
public interface InboxHandler {

	/**
	 * 支持的事件类型（与封套 eventType 匹配）。
	 */
	String eventType();

	/**
	 * 处理事件。抛出异常视为失败，进入重试/退避流程。
	 */
	void handle(InboxEvent event) throws Exception;
}
