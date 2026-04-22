package com.lin.spring.sharding.hash;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 基于 MurmurHash3 与虚拟节点的一致哈希环，用于按业务键选择分片。
 */
public final class ConsistentHashRing {

    private final NavigableMap<Long, Integer> ring = new TreeMap<>();

    /**
     * 构建哈希环。
     *
     * @param shardCount    分片数量
     * @param virtualNodes  每个分片虚拟节点数
     */
    public ConsistentHashRing(int shardCount, int virtualNodes) {
        if (shardCount < 1) {
            throw new IllegalArgumentException("shardCount 必须 >= 1");
        }
        if (virtualNodes < 1) {
            throw new IllegalArgumentException("virtualNodes 必须 >= 1");
        }
        for (int shard = 0; shard < shardCount; shard++) {
            for (int v = 0; v < virtualNodes; v++) {
                long position = hash32("shard:" + shard + ":vnode:" + v);
                ring.put(position, shard);
            }
        }
    }

    /**
     * 根据业务键选择分片索引。
     */
    public int pickShard(String routingKey) {
        if (routingKey == null || routingKey.isEmpty()) {
            throw new IllegalArgumentException("routingKey 不能为空");
        }
        long h = hash32(routingKey);
        Map.Entry<Long, Integer> ceiling = ring.ceilingEntry(h);
        if (ceiling != null) {
            return ceiling.getValue();
        }
        return ring.firstEntry().getValue();
    }

    /**
     * 使用 MurmurHash3 32-bit，将结果视为无符号 32 位整数映射到环上。
     */
    private static long hash32(String key) {
        int hash = Hashing.murmur3_32_fixed().hashString(key, StandardCharsets.UTF_8).asInt();
        return Integer.toUnsignedLong(hash);
    }
}
