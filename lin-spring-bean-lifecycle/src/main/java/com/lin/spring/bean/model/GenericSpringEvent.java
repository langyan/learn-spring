package com.lin.spring.bean.model;

public class GenericSpringEvent<T> {
    private final T payload;
    private final boolean success;

    public GenericSpringEvent(T payload, boolean success) {
        this.payload = payload;
        this.success = success;
    }
    public T getPayload() {
        return payload;
    }
    public boolean isSuccess() {
        return success;
    }
}
