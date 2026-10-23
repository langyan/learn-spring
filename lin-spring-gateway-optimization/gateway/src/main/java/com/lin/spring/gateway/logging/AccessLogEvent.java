package com.lin.spring.gateway.logging;

/** 一次请求的访问日志事件（在请求路径上以纯内存对象构造，微秒级） */
public record AccessLogEvent(
        long timestamp,
        String method,
        String uri,
        int status,
        String userId,
        String authSource,
        String cacheStatus,
        long durationMs) {

    /** 批量落盘时的单行格式（模拟发送到集中式日志系统） */
    public String toLogLine() {
        return String.format("%d|%s|%s|%d|user=%s|auth=%s|cache=%s|%dms",
                timestamp, method, uri, status,
                userId != null ? userId : "-",
                authSource != null ? authSource : "-",
                cacheStatus != null ? cacheStatus : "-",
                durationMs);
    }
}
