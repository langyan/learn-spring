package com.lin.spring.sharding.hash;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 一致哈希环基本行为测试。
 */
class ConsistentHashRingTest {

    @Test
    void pickShard_returnsValidIndex() {
        ConsistentHashRing ring = new ConsistentHashRing(3, 100);
        for (int i = 0; i < 500; i++) {
            int s = ring.pickShard("user-" + i + "@test.com");
            assertThat(s).isBetween(0, 2);
        }
    }

    @Test
    void pickShard_isRoughlyBalanced() {
        ConsistentHashRing ring = new ConsistentHashRing(3, 128);
        Map<Integer, Integer> counts = new HashMap<>();
        for (int i = 0; i < 30_000; i++) {
            int s = ring.pickShard("key-" + i);
            counts.merge(s, 1, Integer::sum);
        }
        for (int s = 0; s < 3; s++) {
            int c = counts.getOrDefault(s, 0);
            assertThat(c).isGreaterThan(5000);
        }
    }
}
