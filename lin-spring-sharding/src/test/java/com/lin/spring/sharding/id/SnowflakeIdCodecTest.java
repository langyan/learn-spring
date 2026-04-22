package com.lin.spring.sharding.id;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 雪花位布局编解码测试。
 */
class SnowflakeIdCodecTest {

    @Test
    void pack_then_extractShardId_roundTrip() {
        long ts = 1_234_567L;
        int shard = 2;
        long seq = 10;
        long id = SnowflakeIdCodec.pack(ts, shard, seq);
        assertThat(SnowflakeIdCodec.extractShardId(id)).isEqualTo(shard);
        assertThat(SnowflakeIdCodec.extractTimestampSinceEpochMs(id)).isEqualTo(ts);
    }
}
