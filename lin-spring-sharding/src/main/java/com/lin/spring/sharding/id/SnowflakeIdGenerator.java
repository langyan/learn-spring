package com.lin.spring.sharding.id;

/**
 * 单机雪花 ID 生成器：同一毫秒内序列递增，跨毫秒归零；分片号写入 ID 便于读路由。
 */
public final class SnowflakeIdGenerator {

    private static final int SEQUENCE_BITS = 12;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;

    private final long epochMs;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    /**
     * @param epochMs 自定义纪元，需稳定不变
     */
    public SnowflakeIdGenerator(long epochMs) {
        this.epochMs = epochMs;
    }

    /**
     * 为指定分片生成下一个全局唯一 ID。
     */
    public synchronized long nextId(int shardId) {
        long now = System.currentTimeMillis() - epochMs;
        if (now < lastTimestamp) {
            throw new IllegalStateException("时钟回拨，拒绝生成 ID");
        }
        if (now == lastTimestamp) {
            sequence = (sequence + 1) & MAX_SEQUENCE;
            if (sequence == 0) {
                now = waitNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = now;
        return SnowflakeIdCodec.pack(now, shardId, sequence);
    }

    private long waitNextMillis(long last) {
        long ts = System.currentTimeMillis() - epochMs;
        while (ts <= last) {
            ts = System.currentTimeMillis() - epochMs;
        }
        return ts;
    }
}
