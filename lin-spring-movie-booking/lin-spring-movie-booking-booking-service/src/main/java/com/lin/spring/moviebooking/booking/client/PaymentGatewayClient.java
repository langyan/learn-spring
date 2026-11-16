package com.lin.spring.moviebooking.booking.client;

import com.lin.spring.moviebooking.common.dto.PaymentCommand;
import com.lin.spring.moviebooking.common.dto.PaymentInitiationResponse;
import com.lin.spring.moviebooking.common.dto.PaymentStatusResponse;

/**
 * Payment gateway port. NEVER called inside a @Transactional method —
 * an external HTTP call while holding a DB connection is how pools die.
 */
public interface PaymentGatewayClient {

    PaymentInitiationResponse createIntent(PaymentCommand command);

    PaymentStatusResponse queryStatus(String gatewayRef);
}
