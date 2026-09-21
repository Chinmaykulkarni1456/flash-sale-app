package com.flashsale.order.service;

import com.flashsale.commons.context.TenantContext;
import com.flashsale.commons.exception.BusinessException;
import com.flashsale.order.client.InventoryClient;
import com.flashsale.order.client.ReservationClient;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.domain.PaymentAttempt;
import com.flashsale.order.domain.PaymentResult;
import com.flashsale.order.domain.PaymentStatus;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.repository.PaymentAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ReservationClient reservationClient;
    private final InventoryClient inventoryClient;
    private final FakePaymentAdapter paymentAdapter;

    @Transactional
    public Order createOrder(UUID reservationId, String skuId, int quantity) {
        String tenantId = TenantContext.getTenantId();

        var reservationDto = reservationClient.getReservation(reservationId);
        if (reservationDto == null || !"ACTIVE".equals(reservationDto.status())) {
            throw new BusinessException("INVALID_RESERVATION", "Reservation is not active or has expired.", HttpStatus.BAD_REQUEST);
        }

        Order order = Order.builder()
                .tenantId(tenantId)
                .reservationId(reservationId)
                .skuId(skuId)
                .quantity(quantity)
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Order savedOrder = orderRepository.save(order);

        // Record initial pending payment attempt
        PaymentAttempt initialAttempt = PaymentAttempt.builder()
                .tenantId(tenantId)
                .orderId(savedOrder.getId())
                .status(PaymentStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        paymentAttemptRepository.save(initialAttempt);

        paymentAdapter.processPayment(savedOrder.getId(), tenantId)
                .thenAccept(paymentResult -> {
                    // FIX 1: Restore tenant context on the async thread so RestClient passes X-Tenant-Id
                    try {
                        TenantContext.setTenantId(tenantId);
                        handlePaymentCallback(savedOrder.getId(), reservationId, skuId, quantity, paymentResult);
                    } finally {
                        TenantContext.clear();
                    }
                });

        return savedOrder;
    }

    @Transactional
    public void timeoutOrder(UUID orderId) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return;
        }

        // CRITICAL ZOMBIE PAYMENT CHECK: Query gateway before blindly timing out
        PaymentResult gatewayResult = paymentAdapter.queryGatewayStatus(orderId);
        if (gatewayResult != null && gatewayResult.isSuccess()) {
            log.error("CRITICAL: Reconciliation caught ZOMBIE PAYMENT success for stale order {}. Processing via callback/refund handler.", orderId);
            handlePaymentCallback(orderId, order.getReservationId(), order.getSkuId(), order.getQuantity(), gatewayResult);
            return;
        }

        // Safe to fail if gateway confirms failure or never completed
        order.setStatus(OrderStatus.PAYMENT_FAILED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        try {
            reservationClient.cancelReservation(order.getReservationId());
            inventoryClient.releaseStock(order.getSkuId(), order.getQuantity());
            log.warn("Order {} timed out due to missing payment callback. Status set to PAYMENT_FAILED. Stock released.", orderId);
        } catch (Exception e) {
            log.error("Failed to release reservation/stock for timed out order {}: {}", orderId, e.getMessage());
        }
    }

    public Order getOrder(UUID id) {
        String tenantId = TenantContext.getTenantId();
        return orderRepository.findByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new BusinessException("ORDER_NOT_FOUND", "Order not found.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public void handlePaymentCallback(UUID orderId, UUID reservationId, String skuId, int quantity, PaymentResult paymentResult) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) return;

        // FIX 2: Idempotency Check - prevent duplicate processing if already finalized
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            log.warn("Order {} already processed. Current status: {}. Ignoring duplicate callback.", orderId, order.getStatus());
            return;
        }

        String tenantId = order.getTenantId();
        order.setPaymentTransactionId(paymentResult.getTransactionId());

        var reservationDto = reservationClient.getReservation(reservationId);
        boolean isReservationExpired = reservationDto == null || !"ACTIVE".equals(reservationDto.status());

        // LATE PAYMENT EDGE CASE: Payment succeeded at gateway, but reservation already expired
        if (paymentResult.isSuccess() && isReservationExpired) {
            log.error("CRITICAL: Late payment success received for expired reservation/order {}. Initiating automatic refund.", orderId);

            paymentAdapter.refundPayment(orderId, paymentResult.getTransactionId(), quantity);
            order.setStatus(OrderStatus.EXPIRED_REFUNDED);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            paymentAttemptRepository.save(PaymentAttempt.builder()
                    .tenantId(tenantId)
                    .orderId(orderId)
                    .status(PaymentStatus.REFUNDED)
                    .transactionId(paymentResult.getTransactionId())
                    .createdAt(Instant.now())
                    .build());
            return;
        }

        // Record standard payment attempt outcome
        PaymentStatus attemptStatus = paymentResult.isSuccess() ? PaymentStatus.SUCCESS : PaymentStatus.FAILED;
        paymentAttemptRepository.save(PaymentAttempt.builder()
                .tenantId(tenantId)
                .orderId(orderId)
                .status(attemptStatus)
                .transactionId(paymentResult.getTransactionId())
                .createdAt(Instant.now())
                .build());

        if (paymentResult.isSuccess()) {
            order.setStatus(OrderStatus.CONFIRMED);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            try {
                reservationClient.confirmReservation(reservationId);
                inventoryClient.commitStock(skuId, quantity);
                log.info("Order {} confirmed and stock committed successfully.", orderId);
            } catch (Exception e) {
                log.error("Failed to commit reservation/stock for confirmed order {}: {}", orderId, e.getMessage());
            }
        } else {
            order.setStatus(OrderStatus.PAYMENT_FAILED);
            order.setUpdatedAt(Instant.now());
            orderRepository.save(order);

            try {
                reservationClient.cancelReservation(reservationId);
                inventoryClient.releaseStock(skuId, quantity);
                log.warn("Order {} failed. Status set to PAYMENT_FAILED. Stock released.", orderId);
            } catch (Exception e) {
                log.error("Failed to release reservation/stock for failed order {}: {}", orderId, e.getMessage());
            }
        }
    }
}