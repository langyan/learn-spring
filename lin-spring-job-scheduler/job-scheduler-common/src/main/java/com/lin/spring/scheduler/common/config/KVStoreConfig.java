package com.lin.spring.scheduler.common.config;

import com.lin.spring.scheduler.common.store.InMemoryKVStore;
import com.lin.spring.scheduler.common.store.KVStore;
import com.lin.spring.scheduler.common.store.RedisKVStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * KVStore 装配：
 * - mysql（默认）profile：Redis（Last Poll Time / 取消标记的真实实现）
 * - dev profile：进程内存实现（无需 Docker）
 */
@Configuration
public class KVStoreConfig {

    @Bean
    @Profile("!dev")
    public KVStore redisKVStore(StringRedisTemplate redisTemplate) {
        return new RedisKVStore(redisTemplate);
    }

    @Bean
    @Profile("dev")
    public KVStore inMemoryKVStore() {
        return new InMemoryKVStore();
    }
}
