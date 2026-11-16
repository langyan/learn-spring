package com.lin.spring.retry.common;

public class RemoteServiceNotAvailableException extends RuntimeException {

    public RemoteServiceNotAvailableException(String message) {
        super(message);
    }
}
