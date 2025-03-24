package com.lin.spring.retry.config;

import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.context.annotation.Bean;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.listener.RetryListenerSupport;

@Configurable
@EnableRetry
public class RetryConfig {

    @Bean
    public RetryListener retryListener() {
        return new RetryListenerSupport() {
            @Override
            public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback,
                    Throwable throwable) {
                System.out.println("Retry attempt #" + context.getRetryCount());
            }
        };
    }

}
