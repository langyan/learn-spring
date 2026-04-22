package com.lin.spring.sharding.config;

import com.lin.spring.sharding.hash.ConsistentHashRing;
import com.lin.spring.sharding.id.SnowflakeIdGenerator;
import com.lin.spring.sharding.routing.ShardRoutingDataSource;
import com.zaxxer.hikari.HikariDataSource;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 多物理数据源与路由 {@link DataSource}、一致哈希环、雪花生成器装配。
 */
@Configuration
public class ShardingDataSourceConfig {

    /**
     * 为每个分片构建独立连接池。
     */
    @Bean
    public Map<Integer, DataSource> shardDataSources(ShardingProperties properties) {
        Map<Integer, DataSource> map = new HashMap<>();
        for (int i = 0; i < properties.getShardCount(); i++) {
            ShardingProperties.ShardDataSourceProps p =
                    properties.getDatasources().get(String.valueOf(i));
            if (p == null || p.getJdbcUrl() == null) {
                throw new IllegalStateException("缺少分片 " + i + " 的数据源配置 app.sharding.datasources." + i);
            }
            HikariDataSource ds = new HikariDataSource();
            ds.setJdbcUrl(p.getJdbcUrl());
            ds.setUsername(p.getUsername());
            ds.setPassword(p.getPassword() == null ? "" : p.getPassword());
            ds.setPoolName("shard-" + i + "-pool");
            map.put(i, ds);
        }
        return map;
    }

    /**
     * 应用层统一注入的路由数据源（JPA 使用此 Bean）。
     */
    @Bean
    @Primary
    public DataSource routingDataSource(Map<Integer, DataSource> shardDataSources, ShardingProperties properties) {
        ShardRoutingDataSource routing = new ShardRoutingDataSource();
        Map<Object, Object> targets = new HashMap<>(shardDataSources);
        routing.setTargetDataSources(targets);
        routing.setDefaultTargetDataSource(shardDataSources.get(0));
        routing.afterPropertiesSet();
        return routing;
    }

    /**
     * 一致哈希环，分片数量或虚拟节点变更后需重建（此处为启动期固定配置）。
     */
    @Bean
    public ConsistentHashRing consistentHashRing(ShardingProperties properties) {
        return new ConsistentHashRing(properties.getShardCount(), properties.getVirtualNodes());
    }

    /**
     * 雪花 ID 生成器，epoch 与 {@link ShardingProperties#getSnowflakeEpochMs()} 对齐。
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(ShardingProperties properties) {
        return new SnowflakeIdGenerator(properties.getSnowflakeEpochMs());
    }
}
