package com.lin.spring.ticketrace.booking.service;

import com.lin.spring.ticketrace.common.dto.PaymentCommand;
import com.lin.spring.ticketrace.common.dto.PaymentResult;

public interface PaymentGatewayClient {

    PaymentResult process(PaymentCommand command);
}
