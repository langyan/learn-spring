package com.lin.spring.chat.gateway.relay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 本实例的 per-gateway topic 名称与消费组（对齐参考文章方案二：
 * 每个网关实例订阅自己的专属 topic gateway-{serverId}，Kafka 提供持久化与重放）。
 * groupId 按实例隔离，保证多实例不做同组分摊。
 */
@Component
public class GatewayTopicNames {

    private final String serverId;

    public GatewayTopicNames(@Value("${app.gateway.server-id}") String serverId) {
        this.serverId = serverId;
    }

    public String relayTopic() {
        return "gateway-" + serverId;
    }

    public String groupId() {
        return "gw-" + serverId;
    }
}
