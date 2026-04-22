package com.lin.spring.sharding.routing;

/**
 * 当前线程绑定的分片路由键，在获取 JDBC 连接前必须已设置。
 */
public final class ShardContext {

    private static final ThreadLocal<Integer> HOLDER = new ThreadLocal<>();

    private ShardContext() {
    }

    /**
     * 设置当前请求应路由到的分片索引。
     */
    public static void set(int shard) {
        HOLDER.set(shard);
    }

    /**
     * 获取当前分片索引；若未设置则为 null。
     */
    public static Integer get() {
        return HOLDER.get();
    }

    /**
     * 清理线程本地状态，避免线程池复用导致串分片。
     */
    public static void clear() {
        HOLDER.remove();
    }
}
