package com.lin.spring.gateway.support;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 过滤器短路响应（401/429/502）的 JSON 输出工具 */
public final class JsonResponses {

    public static Mono<Void> write(ServerWebExchange exchange, HttpStatus status, Map<String, String> body) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        StringBuilder json = new StringBuilder("{");
        body.forEach((k, v) -> json.append("\"").append(k).append("\":\"").append(v).append("\","));
        if (json.length() > 1) {
            json.setLength(json.length() - 1);
        }
        json.append("}");

        byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        return response.writeWith(Mono.just(buffer));
    }

    private JsonResponses() {
    }
}
