package com.lin.spring.idempotency.exception;

public class IdempotencyConflictException extends RuntimeException {

	public IdempotencyConflictException(String message) {
		super(message);
	}
}
