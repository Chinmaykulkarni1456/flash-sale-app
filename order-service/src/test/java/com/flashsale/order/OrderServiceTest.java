package com.flashsale.order;

import com.flashsale.commons.context.TenantContext;
import com.flashsale.commons.exception.BusinessException;
import com.flashsale.order.client.InventoryClient;
import com.flashsale.order.client.ReservationClient;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.domain.PaymentResult;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.repository.PaymentAttemptRepository;
import com.flashsale.order.service.FakePaymentAdapter;
import com.flashsale.order.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentAttemptRepository paymentAttemptRepository;

    @MockBean
    private ReservationClient reservationClient;

    @MockBean
    private InventoryClient inventoryClient;

    @MockBean
    private FakePaymentAdapter paymentAdapter;

    private final String tenantId = "tenant-test";

    @BeforeEach
    void setUp() {
        paymentAttemptRepository.deleteAll();
        orderRepository.deleteAll();
        TenantContext.setTenantId(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void testCreateOrder_WithActiveReservation_ShouldSucceedAndStartPending() {
        UUID reservationId = UUID.randomUUID();
        String skuId = "sku-prod-1";
        int quantity = 2;

        ReservationClient.ReservationDto mockRes = new ReservationClient.ReservationDto(
                reservationId, skuId, quantity, "ACTIVE", Instant.now().plusSeconds(300)
        );
        when(reservationClient.getReservation(reservationId)).thenReturn(mockRes);

        // Return an uncompleted future so order stays in PENDING_PAYMENT during assertion
        when(paymentAdapter.processPayment(any(UUID.class), anyString()))
                .thenReturn(new CompletableFuture<>());

        Order createdOrder = orderService.createOrder(reservationId, skuId, quantity);

        assertNotNull(createdOrder.getId());
        assertEquals(tenantId, createdOrder.getTenantId());
        assertEquals(reservationId, createdOrder.getReservationId());
        assertEquals(OrderStatus.PENDING_PAYMENT, createdOrder.getStatus());

        verify(reservationClient, times(1)).getReservation(reservationId);
    }

    @Test
    void testCreateOrder_WithInvalidReservation_ShouldThrowException() {
        UUID reservationId = UUID.randomUUID();
        when(reservationClient.getReservation(reservationId)).thenReturn(null);

        assertThrows(BusinessException.class, () -> {
            orderService.createOrder(reservationId, "sku-1", 1);
        });

        verify(reservationClient, times(1)).getReservation(reservationId);
    }

    @Test
    void testHandlePaymentCallback_Success_ShouldConfirmOrderAndCommitStock() {
        UUID reservationId = UUID.randomUUID();
        String skuId = "sku-prod-1";
        int quantity = 2;

        Order order = Order.builder()
                .tenantId(tenantId)
                .reservationId(reservationId)
                .skuId(skuId)
                .quantity(quantity)
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        order = orderRepository.save(order);

        ReservationClient.ReservationDto mockRes = new ReservationClient.ReservationDto(
                reservationId, skuId, quantity, "ACTIVE", Instant.now().plusSeconds(300)
        );
        when(reservationClient.getReservation(reservationId)).thenReturn(mockRes);

        PaymentResult paymentResult = new PaymentResult("TXN-SUCCESS", true, "Approved");
        orderService.handlePaymentCallback(order.getId(), reservationId, skuId, quantity, paymentResult);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, updatedOrder.getStatus());
        verify(reservationClient, times(1)).confirmReservation(reservationId);
        verify(inventoryClient, times(1)).commitStock(skuId, quantity);
    }

    @Test
    void testHandlePaymentCallback_Failure_ShouldFailOrderAndReleaseStock() {
        UUID reservationId = UUID.randomUUID();
        String skuId = "sku-prod-1";
        int quantity = 2;

        Order order = Order.builder()
                .tenantId(tenantId)
                .reservationId(reservationId)
                .skuId(skuId)
                .quantity(quantity)
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        order = orderRepository.save(order);

        ReservationClient.ReservationDto mockRes = new ReservationClient.ReservationDto(
                reservationId, skuId, quantity, "ACTIVE", Instant.now().plusSeconds(300)
        );
        when(reservationClient.getReservation(reservationId)).thenReturn(mockRes);

        PaymentResult paymentResult = new PaymentResult("TXN-FAIL", false, "Declined");
        orderService.handlePaymentCallback(order.getId(), reservationId, skuId, quantity, paymentResult);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PAYMENT_FAILED, updatedOrder.getStatus());
        verify(reservationClient, times(1)).cancelReservation(reservationId);
        verify(inventoryClient, times(1)).releaseStock(skuId, quantity);
    }

    @Test
    void testHandlePaymentCallback_LatePayment_ShouldRefund() {
        UUID reservationId = UUID.randomUUID();
        String skuId = "sku-prod-1";
        int quantity = 2;

        Order order = Order.builder()
                .tenantId(tenantId)
                .reservationId(reservationId)
                .skuId(skuId)
                .quantity(quantity)
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        order = orderRepository.save(order);

        // Reservation is expired/inactive
        ReservationClient.ReservationDto mockRes = new ReservationClient.ReservationDto(
                reservationId, skuId, quantity, "EXPIRED", Instant.now().minusSeconds(10)
        );
        when(reservationClient.getReservation(reservationId)).thenReturn(mockRes);
        when(paymentAdapter.refundPayment(any(UUID.class), anyString(), anyInt())).thenReturn(true);

        PaymentResult paymentResult = new PaymentResult("TXN-LATE", true, "Approved");
        orderService.handlePaymentCallback(order.getId(), reservationId, skuId, quantity, paymentResult);

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.EXPIRED_REFUNDED, updatedOrder.getStatus());
        verify(paymentAdapter, times(1)).refundPayment(order.getId(), "TXN-LATE", quantity);
    }

    @Test
    void testTimeoutOrder_NormalTimeout_ShouldFailAndReleaseStock() {
        UUID reservationId = UUID.randomUUID();
        String skuId = "sku-prod-1";
        int quantity = 2;

        Order order = Order.builder()
                .tenantId(tenantId)
                .reservationId(reservationId)
                .skuId(skuId)
                .quantity(quantity)
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(Instant.now().minusSeconds(300))
                .updatedAt(Instant.now().minusSeconds(300))
                .build();
        order = orderRepository.save(order);

        // Gateway returns null (no payment recorded)
        when(paymentAdapter.queryGatewayStatus(order.getId())).thenReturn(null);

        orderService.timeoutOrder(order.getId());

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.PAYMENT_FAILED, updatedOrder.getStatus());
        verify(reservationClient, times(1)).cancelReservation(reservationId);
        verify(inventoryClient, times(1)).releaseStock(skuId, quantity);
    }

    @Test
    void testTimeoutOrder_ZombiePayment_ShouldReconcileAndConfirm() {
        UUID reservationId = UUID.randomUUID();
        String skuId = "sku-prod-1";
        int quantity = 2;

        Order order = Order.builder()
                .tenantId(tenantId)
                .reservationId(reservationId)
                .skuId(skuId)
                .quantity(quantity)
                .status(OrderStatus.PENDING_PAYMENT)
                .createdAt(Instant.now().minusSeconds(300))
                .updatedAt(Instant.now().minusSeconds(300))
                .build();
        order = orderRepository.save(order);

        ReservationClient.ReservationDto mockRes = new ReservationClient.ReservationDto(
                reservationId, skuId, quantity, "ACTIVE", Instant.now().plusSeconds(300)
        );
        when(reservationClient.getReservation(reservationId)).thenReturn(mockRes);

        // Gateway status check reveals a successful "zombie" payment that missed the webhook
        PaymentResult zombieSuccess = new PaymentResult("TXN-ZOMBIE", true, "Approved");
        when(paymentAdapter.queryGatewayStatus(order.getId())).thenReturn(zombieSuccess);

        orderService.timeoutOrder(order.getId());

        Order updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
        assertEquals(OrderStatus.CONFIRMED, updatedOrder.getStatus());
        verify(reservationClient, times(1)).confirmReservation(reservationId);
        verify(inventoryClient, times(1)).commitStock(skuId, quantity);
    }
}