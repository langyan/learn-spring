package com.lin.spring.scheduler.common.config;

import com.lin.spring.scheduler.common.messaging.KafkaTopics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.EmbeddedKafkaKraftBroker;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * dev profile 专用：在本机固定端口 9092 拉起嵌入式 Kafka（KRaft，无需 Docker）。
 *
 * 三个服务以 dev profile 同时运行时，首个启动的服务托管 broker（抢占 9092 端口），
 * 其余服务探测到端口已被占用则跳过启动，直接作为客户端接入 localhost:9092。
 * 主题 jobs.run / jobs.retry / jobs.dead 由 broker 预建（3 分区）。
 */
@Slf4j
@Configuration
@Profile("dev")
public class DevKafkaBrokerConfig {

    private static final int BROKER_PORT = 9092;

    @Bean(destroyMethod = "destroy")
    public EmbeddedKafkaBroker devKafkaBroker() {
        if (!isPortAvailable(BROKER_PORT)) {
            log.info("Embedded kafka broker already running on port {} (hosted by another service), connecting as client", BROKER_PORT);
            return null;
        }
        log.info("Starting embedded KRaft kafka broker on port {} with topics {}", BROKER_PORT, String.join(",", KafkaTopics.ALL));
        EmbeddedKafkaKraftBroker broker = new EmbeddedKafkaKraftBroker(1, 3, KafkaTopics.ALL);
        broker.kafkaPorts(BROKER_PORT);
        return broker;
    }

    private static boolean isPortAvailable(int port) {
        try (ServerSocket ignored = new ServerSocket(port)) {
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
