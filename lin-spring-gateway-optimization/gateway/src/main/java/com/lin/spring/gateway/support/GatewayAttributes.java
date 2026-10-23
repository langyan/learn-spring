package com.lin.spring.gateway.support;

/** 跨过滤器传递的 exchange attribute key */
public final class GatewayAttributes {

    /** 认证通过的用户名（AuthGlobalFilter 写入，限流/日志读取） */
    public static final String USER_ID = "gateway.userId";

    /** 响应缓存状态：HIT / MISS / COALESCED（缓存过滤器写入，日志读取） */
    public static final String CACHE_STATUS = "gateway.cacheStatus";

    /** 认证来源：INTROSPECTION_CACHE / LOCAL_VERIFY（认证过滤器写入，日志读取） */
    public static final String AUTH_SOURCE = "gateway.authSource";

    private GatewayAttributes() {
    }
}
