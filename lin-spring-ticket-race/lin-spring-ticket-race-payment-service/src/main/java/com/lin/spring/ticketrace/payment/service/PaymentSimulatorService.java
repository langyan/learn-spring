package com.lin.spring.ticketrace.payment.service;

import com.lin.spring.ticketrace.common.dto.PaymentCommand;
import com.lin.spring.ticketrace.common.dto.PaymentResult;
import com.lin.spring.ticketrace.common.enums.PaymentStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PaymentSimulatorService {

    public PaymentResult process(PaymentCommand command) {
        sleep(command.delayMillis());

        PaymentStatus status = resolveStatus(command);
        String message = switch (status) {
            case SUCCESS -> "payment accepted";
            case FAILED -> "payment failed by test flag";
            case TIMEOUT -> "payment timed out";
            case INIT -> "payment initialized";
        };

        return new PaymentResult(command.bookingNo(), status, message, Instant.now());
    }

    private PaymentStatus resolveStatus(PaymentCommand command) {
        if (command.forceTimeout()) {
            return PaymentStatus.TIMEOUT;
        }
        if (command.forceFailure()) {
            return PaymentStatus.FAILED;
        }
        return PaymentStatus.SUCCESS;
    }

    private void sleep(long delayMillis) {
        if (delayMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Payment simulation interrupted", exception);
        }
    }
}
