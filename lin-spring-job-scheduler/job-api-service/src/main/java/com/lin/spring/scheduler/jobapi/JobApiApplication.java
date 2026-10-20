package com.lin.spring.scheduler.jobapi;

import com.lin.spring.scheduler.common.entity.Job;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;

/**
 * 作业摄入服务：REST API（创建/详情/状态/更新/取消/立即执行）+ 压测端点。
 *
 * scanBasePackages 上提到 com.lin.spring.scheduler，使 common 中的
 * 主题/存储/嵌入式 broker 配置被扫描装配。
 */
@SpringBootApplication(scanBasePackages = "com.lin.spring.scheduler")
@EntityScan(basePackages = "com.lin.spring.scheduler.common.entity")
public class JobApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobApiApplication.class, args);
    }
}
