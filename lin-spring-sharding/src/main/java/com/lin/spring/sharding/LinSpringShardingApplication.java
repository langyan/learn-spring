package com.lin.spring.sharding;

import com.lin.spring.sharding.config.ShardingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 分片示例应用入口：路由数据源 + JPA + AOP 在事务前设置分片上下文。
 */
@SpringBootApplication
@EnableConfigurationProperties(ShardingProperties.class)
public class LinSpringShardingApplication {

    public static void main(String[] args) {
        SpringApplication.run(LinSpringShardingApplication.class, args);
    }
}
