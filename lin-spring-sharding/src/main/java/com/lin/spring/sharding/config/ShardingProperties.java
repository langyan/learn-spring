package com.lin.spring.sharding.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 分片相关配置：逻辑分片数量、虚拟节点、各物理库连接。
 */
@Data
@Validated
@ConfigurationProperties(prefix = "app.sharding")
public class ShardingProperties {

    /**
     * 逻辑分片数量，用于构建一致哈希环。
     */
    @Min(1)
    private int shardCount = 3;

    /**
     * 每个物理分片在环上的虚拟节点数，典型 64–256。
     */
    @Min(1)
    private int virtualNodes = 100;

    /**
     * 雪花 ID 自定义纪元（毫秒），需小于当前时间。
     */
    @Min(0)
    private long snowflakeEpochMs = 1704067200000L;

    /**
     * 分片索引到数据源配置的映射，键为分片编号字符串。
     */
    @NotEmpty
    private Map<String, ShardDataSourceProps> datasources = new LinkedHashMap<>();

    /**
     * 单个分片数据源的 JDBC 属性。
     */
    @Data
    public static class ShardDataSourceProps {
        private String jdbcUrl;
        private String username;
        private String password;
    }
}
