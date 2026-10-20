package com.lin.spring.scheduler.executor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * 执行人服务：从 Kafka jobs.run / jobs.retry 拉取作业并执行业务逻辑。
 * - 执行前 Redis 取消预检（JOB_{id}_CANCEL）
 * - 执行期间每 10s 心跳（更新 JobRun.modified_time）
 * - 瞬时错误 -> jobs.retry；重试耗尽 -> jobs.dead（DLQ）
 */
@SpringBootApplication(scanBasePackages = "com.lin.spring.scheduler")
@EntityScan(basePackages = "com.lin.spring.scheduler.common.entity")
public class ExecutorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExecutorApplication.class, args);
    }
}
