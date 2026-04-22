package com.lin.spring.sharding.routing;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * 基于 {@link ShardContext} 在获取连接时选择目标物理数据源。
 * <p>上下文为空时回退到 0 号分片，供 JPA/Hibernate 启动期元数据与 DDL 使用；业务读写仍应通过
 * {@code @Sharded}/{@code @ShardRoutedRead} 在事务开始前显式设置分片。</p>
 */
public class ShardRoutingDataSource extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        Integer shard = ShardContext.get();
        return shard == null ? 0 : shard;
    }
}
