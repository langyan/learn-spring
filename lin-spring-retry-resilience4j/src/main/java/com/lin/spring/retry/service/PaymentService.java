package com.lin.spring.retry.service;

import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import com.lin.spring.retry.common.RemoteServiceNotAvailableException;

@Service
public class PaymentService {

    @Retryable(value = {
            RemoteServiceNotAvailableException.class }, maxAttempts = 3, backoff = @Backoff(delay = 2000, multiplier = 2))
    public void processPayment(String transactionId) {
        // Call to a remote payment service
        // externalPaymentService.call(transactionId);
        try {
            Thread.sleep(1000*5);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    @Recover
    public void recover(RemoteServiceNotAvailableException e, String transactionId) {
        // Recovery action, such as notifying the user or logging the failure
        System.out.println("Payment processing failed for transaction: " + transactionId);
    }
}
