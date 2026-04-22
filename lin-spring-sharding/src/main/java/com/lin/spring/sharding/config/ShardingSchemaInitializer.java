package com.lin.spring.sharding.config;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 在各物理分片上执行相同 DDL，避免路由数据源导致仅默认库建表的问题。
 */
@Component
public class ShardingSchemaInitializer {

    private static final Logger log = LoggerFactory.getLogger(ShardingSchemaInitializer.class);

    private static final String DDL =
            """
            CREATE TABLE IF NOT EXISTS app_user (
              id BIGINT NOT NULL PRIMARY KEY,
              email VARCHAR(255) NOT NULL,
              shard_id INT NOT NULL
            );
            """;

    private final Map<Integer, DataSource> shardDataSources;

    public ShardingSchemaInitializer(Map<Integer, DataSource> shardDataSources) {
        this.shardDataSources = shardDataSources;
    }

    /**
     * 应用就绪后为每个分片库执行建表（演示用；生产建议使用迁移工具）。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initSchema() {
        for (Map.Entry<Integer, DataSource> e : shardDataSources.entrySet()) {
            try (Connection c = e.getValue().getConnection();
                    Statement st = c.createStatement()) {
                st.execute(DDL);
                log.info("分片 {} 建表校验完成", e.getKey());
            } catch (Exception ex) {
                throw new IllegalStateException("分片 " + e.getKey() + " 初始化失败", ex);
            }
        }
    }
}
