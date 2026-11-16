package com.lin.spring.grpc.client.web;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;

/**
 * gRPC Status Code -> HTTP 状态码 的统一翻译器。
 *
 * <pre>
 * OK                 -> 200（正常返回，不经过这里）
 * INVALID_ARGUMENT   -> 400
 * UNAUTHENTICATED    -> 401
 * PERMISSION_DENIED  -> 403
 * NOT_FOUND          -> 404
 * ALREADY_EXISTS     -> 409
 * DEADLINE_EXCEEDED  -> 504
 * UNAVAILABLE        -> 503
 * 其他（INTERNAL 等） -> 500
 * </pre>
 */
@Component
public class GrpcExceptionMapper {

    public ResponseEntity<Map<String, String>> toHttpResponse(StatusRuntimeException e) {
        HttpStatus httpStatus = toHttpStatus(e.getStatus().getCode());
        String description = e.getStatus().getDescription();
        if (description == null || description.isBlank()) {
            description = e.getStatus().getCode().name();
        }
        return ResponseEntity.status(httpStatus)
                .body(Map.of(
                        "error", description,
                        "grpcStatus", e.getStatus().getCode().name()));
    }

    HttpStatus toHttpStatus(Status.Code code) {
        return switch (code) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;                       // 404
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;              // 400
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;               // 403
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;              // 401
            case ALREADY_EXISTS -> HttpStatus.CONFLICT;                   // 409
            case DEADLINE_EXCEEDED -> HttpStatus.GATEWAY_TIMEOUT;         // 504
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;           // 503
            default -> HttpStatus.INTERNAL_SERVER_ERROR;                  // 500
        };
    }
}
