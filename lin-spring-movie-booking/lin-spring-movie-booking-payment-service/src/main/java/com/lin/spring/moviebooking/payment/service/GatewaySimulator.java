package com.lin.spring.moviebooking.payment.service;

import com.lin.spring.moviebooking.common.dto.PaymentCommand;
import com.lin.spring.moviebooking.common.dto.PaymentInitiationResponse;
import com.lin.spring.moviebooking.common.dto.PaymentStatusResponse;
import com.lin.spring.moviebooking.common.dto.WebhookPayload;
import com.lin.spring.moviebooking.common.enums.PaymentStatus;
import com.lin.spring.moviebooking.payment.client.WebhookDeliveryClient;
import com.lin.spring.moviebooking.payment.config.PaymentGatewayProperties;
import com.lin.spring.moviebooking.payment.domain.PaymentIntent;
import com.lin.spring.moviebooking.payment.store.IntentStore;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The simulated PSP. Scenarios (steered per-request via PaymentCommand):
 * <ul>
 *   <li>NORMAL    — channel latency, then a successful webhook</li>
 *   <li>FAIL      — short delay, failing webhook (INSUFFICIENT_FUNDS)</li>
 *   <li>LATE      — webhook arrives AFTER the (short demo) hold expires,
 *                   exercising booking's grace-window recovery / refund</li>
 *   <li>DUPLICATE — five identical webhooks, one effect</li>
 *   <li>LOST      — no webhook at all; booking must resolve UNKNOWN by
 *                   querying status explicitly</li>
 * </ul>
 */
@Slf4j
@Service
public class GatewaySimulator {

    private final IntentStore intentStore;
    private final WebhookDeliveryClient webhookClient;
    private final PaymentGatewayProperties properties;
    private final ScheduledExecutorService scheduler =
            Executors.newScheduledThreadPool(4, r -> {
                Thread t = new Thread(r, "gateway-simulator");
                t.setDaemon(true);
                return t;
            });

    public GatewaySimulator(IntentStore intentStore,
                            WebhookDeliveryClient webhookClient,
                            PaymentGatewayProperties properties) {
        this.intentStore = intentStore;
        this.webhookClient = webhookClient;
        this.properties = properties;
    }

    /** Idempotent: same (booking + idemKey) -> same intent, never two charges. */
    public PaymentInitiationResponse createIntent(PaymentCommand command) {
        String idemKey = command.bookingReference() + "|" + command.idempotencyKey();
        PaymentIntent intent = intentStore.getOrCreate(idemKey, () -> {
            String gatewayRef = "pay_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            return new PaymentIntent(
                    gatewayRef, gatewayRef,
                    command.bookingReference(), command.userId(), command.amount(),
                    command.channel() == null ? "UPI" : command.channel(),
                    command.scenario() == null ? "NORMAL" : command.scenario().toUpperCase(),
                    command.idempotencyKey(),
                    PaymentStatus.PENDING, null, Instant.now(), null);
        });

        scheduleProcessing(intent);
        return new PaymentInitiationResponse(intent.getPaymentId(), intent.getBookingReference(),
                intent.getGatewayRef(), intent.getStatus(), clientSecret(intent));
    }

    /** Status query — the ONLY way booking resolves UNKNOWN outcomes. */
    public PaymentStatusResponse status(String gatewayRef) {
        PaymentIntent intent = intentStore.findByGatewayRef(gatewayRef)
                .orElseThrow(() -> new IllegalArgumentException("Unknown gatewayRef: " + gatewayRef));
        return new PaymentStatusResponse(intent.getPaymentId(), intent.getBookingReference(),
                intent.getGatewayRef(), intent.getStatus(), intent.getFailureCode());
    }

    // ------------------------------------------------------------------

    private void scheduleProcessing(PaymentIntent intent) {
        long delay = switch (intent.getScenario()) {
            case "FAIL" -> 500L;
            case "LATE" -> properties.getLateDelayMs();
            case "LOST" -> properties.getLateDelayMs();   // completes, but never calls back
            default -> "CARD".equalsIgnoreCase(intent.getChannel())
                    ? properties.getCardDelayMs() : properties.getUpiDelayMs();
        };
        scheduler.schedule(() -> process(intent), delay, TimeUnit.MILLISECONDS);
        log.info("Intent {} scheduled: scenario={}, channel={}, delay={}ms",
                intent.getGatewayRef(), intent.getScenario(), intent.getChannel(), delay);
    }

    private void process(PaymentIntent intent) {
        boolean success = !"FAIL".equals(intent.getScenario());
        intent.setStatus(success ? PaymentStatus.SUCCEEDED : PaymentStatus.FAILED);
        intent.setFailureCode(success ? null : "INSUFFICIENT_FUNDS");
        intent.setCompletedAt(Instant.now());

        if ("LOST".equals(intent.getScenario())) {
            log.warn("Intent {}: webhook LOST on purpose — booking must poll status",
                    intent.getGatewayRef());
            return;
        }

        WebhookPayload payload = new WebhookPayload(
                intent.getPaymentId(), intent.getBookingReference(), intent.getGatewayRef(),
                success ? "SUCCEEDED" : "FAILED", intent.getFailureCode());

        int deliveries = "DUPLICATE".equals(intent.getScenario())
                ? properties.getDuplicateDeliveries() : 1;
        for (int i = 1; i <= deliveries; i++) {
            // Sequential deliveries mimic a PSP redelivering until 2xx.
            boolean accepted = webhookClient.deliver(payload);
            log.info("Webhook delivery {}/{} for {}: {}", i, deliveries,
                    intent.getGatewayRef(), accepted ? "accepted" : "rejected");
        }
    }

    private String clientSecret(PaymentIntent intent) {
        return "secret_" + intent.getGatewayRef().hashCode();
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
