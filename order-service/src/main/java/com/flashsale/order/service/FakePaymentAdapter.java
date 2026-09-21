package com.flashsale.order.service;

import com.flashsale.order.domain.PaymentResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Component
public class FakePaymentAdapter {

    private final Random random = new Random();
    private final ExecutorService paymentExecutor = Executors.newCachedThreadPool();

    // Ledger to simulate gateway source of truth for reconciliation checks
    private final ConcurrentMap<UUID, PaymentResult> gatewayLedger = new ConcurrentHashMap<>();

    public CompletableFuture<PaymentResult> processPayment(UUID orderId, String tenantId) {
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("Starting async payment simulation for Order: {} [Tenant: {}], Transaction: {}", orderId, tenantId, transactionId);

        return CompletableFuture.supplyAsync(() -> {
            try {
                int delayMillis = random.nextInt(150) + 50;
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Payment simulation interrupted for Order: {}", orderId);
                PaymentResult failedResult = new PaymentResult(transactionId, false, "Interrupted");
                gatewayLedger.put(orderId, failedResult);
                return failedResult;
            }

            boolean success = random.nextDouble() < 0.90;
            PaymentResult result = new PaymentResult(transactionId, success, success ? "Approved" : "Declined");

            // Store result in gateway ledger so reconciliation can verify it later
            gatewayLedger.put(orderId, result);

            log.info("Payment simulation completed for Order: {}. Result: {}", orderId, success ? "SUCCESS" : "FAILED");
            return result;
        }, paymentExecutor);
    }

    /**
     * Active gateway reconciliation check: queries the authoritative state of an order's payment.
     * @return PaymentResult if recorded, or null if the gateway never received/processed it.
     */
    public PaymentResult queryGatewayStatus(UUID orderId) {
        return gatewayLedger.get(orderId);
    }

    public boolean refundPayment(UUID orderId, String transactionId, int quantity) {
        log.warn(">>> REFUND EXECUTED: Refund issued for Order: {}, Transaction ID: {}, Quantity: {} <<<", orderId, transactionId, quantity);
        return true;
    }
}