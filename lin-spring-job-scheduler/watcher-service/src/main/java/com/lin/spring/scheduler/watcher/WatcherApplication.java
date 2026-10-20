package com.lin.spring.scheduler.watcher;

import com.lin.spring.scheduler.common.entity.Job;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 守望者服务（调度员的心跳）：
 * - 识别时间到位的作业并移入执行队列（20s 轮询 + Kafka 派发）
 * - 僵尸侦测：心跳超时的 RUNNING 作业判定执行节点已死，重触发到 retry 主题
 */
@SpringBootApplication(scanBasePackages = "com.lin.spring.scheduler")
@EntityScan(basePackages = "com.lin.spring.scheduler.common.entity")
@EnableScheduling
public class WatcherApplication {

    public static void main(String[] args) {
        SpringApplication.run(WatcherApplication.class, args);
    }
}
