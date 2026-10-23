package com.lin.spring.gateway.cache;

import java.time.Instant;

/**
 * 网关层缓存的下游响应（文章中的"边缘缓存"在网关侧的等价物）。
 *
 * 只保留回放必需的部分：状态码、Content-Type、body。
 * 逐跳头（Connection/Transfer-Encoding/Keep-Alive 等）不缓存。
 */
public record CachedResponse(int status, String contentType, byte[] body, Instant cachedAt) {

    public boolean isCacheableStatus() {
        return status >= 200 && status < 300;
    }
}
