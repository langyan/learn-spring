package com.lin.spring.webhook.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * 统一异常处理。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

	@ExceptionHandler(SsrfBlockedException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleSsrf(SsrfBlockedException ex) {
		log.warn("SSRF 拦截: {}", ex.getMessage());
		return Map.of("error", "SSRF_BLOCKED", "message", ex.getMessage());
	}

	@ExceptionHandler(EndpointNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, Object> handleEndpointNotFound(EndpointNotFoundException ex) {
		return Map.of("error", "ENDPOINT_NOT_FOUND", "message", ex.getMessage());
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
			.map(error -> error.getField() + ": " + error.getDefaultMessage())
			.reduce((a, b) -> a + "; " + b)
			.orElse("参数校验失败");
		return Map.of("error", "VALIDATION_FAILED", "message", message);
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, Object> handleIllegalArgument(IllegalArgumentException ex) {
		return Map.of("error", "BAD_REQUEST", "message", ex.getMessage());
	}

	@ExceptionHandler(Exception.class)
	@ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
	public Map<String, Object> handleUnexpected(Exception ex) {
		log.error("未预期异常", ex);
		return Map.of("error", "INTERNAL_ERROR", "message", String.valueOf(ex.getMessage()));
	}
}
