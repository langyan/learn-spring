package com.lin.spring.sharding.id;

/**
 * 雪花 ID 位布局编解码：时间戳 | 分片 ID | 序列号。
 * <p>布局（低位到高位）：12 位 sequence，10 位 shardId，41 位 timestamp（相对 epoch 的毫秒）。</p>
 */
public final class SnowflakeIdCodec {

    private static final int SEQUENCE_BITS = 12;
    private static final int SHARD_BITS = 10;

    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_SHARD = (1L << SHARD_BITS) - 1;

    private SnowflakeIdCodec() {
    }

    /**
     * 将时间戳、分片、序列打包为 64 位 ID（最高位保持为 0，保证为正数语义）。
     */
    public static long pack(long timestampSinceEpochMs, int shardId, long sequence) {
        if (shardId < 0 || shardId > MAX_SHARD) {
            throw new IllegalArgumentException("shardId 超出范围 [0, " + MAX_SHARD + "]");
        }
        if (sequence < 0 || sequence > MAX_SEQUENCE) {
            throw new IllegalArgumentException("sequence 超出范围 [0, " + MAX_SEQUENCE + "]");
        }
        if (timestampSinceEpochMs < 0) {
            throw new IllegalArgumentException("timestampSinceEpochMs 不能为负");
        }
        return (timestampSinceEpochMs << (SEQUENCE_BITS + SHARD_BITS)) | ((long) shardId << SEQUENCE_BITS) | sequence;
    }

    /**
     * 从 ID 中解析分片编号。
     */
    public static int extractShardId(long id) {
        return (int) ((id >>> SEQUENCE_BITS) & MAX_SHARD);
    }

    /**
     * 从 ID 中解析相对 epoch 的毫秒时间戳。
     */
    public static long extractTimestampSinceEpochMs(long id) {
        return id >>> (SEQUENCE_BITS + SHARD_BITS);
    }
}
