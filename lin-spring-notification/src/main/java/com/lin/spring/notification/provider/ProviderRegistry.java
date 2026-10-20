package com.lin.spring.notification.provider;

import com.lin.spring.notification.domain.NotificationChannel;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 按渠道查找 NotificationProvider 实现。
 */
@Component
public class ProviderRegistry {

	private final Map<NotificationChannel, NotificationProvider> providers;

	public ProviderRegistry(List<NotificationProvider> providerList) {
		this.providers = new EnumMap<>(NotificationChannel.class);
		for (NotificationProvider provider : providerList) {
			providers.put(provider.channel(), provider);
		}
	}

	/**
	 * 获取指定渠道的 Provider。
	 *
	 * @param channel 通知渠道
	 * @return Provider 实现
	 */
	public NotificationProvider get(NotificationChannel channel) {
		NotificationProvider provider = providers.get(channel);
		if (provider == null) {
			throw new IllegalStateException("未注册 Provider: " + channel);
		}
		return provider;
	}
}
