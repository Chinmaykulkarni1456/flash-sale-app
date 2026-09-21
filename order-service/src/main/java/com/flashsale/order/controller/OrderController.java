package com.flashsale.order.controller;

import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderRequest;
import com.flashsale.order.domain.PaymentAttempt;
import com.flashsale.order.repository.PaymentAttemptRepository;
import com.flashsale.order.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final PaymentAttemptRepository paymentAttemptRepository;


    @PostMapping
    public ResponseEntity<Order> createOrder(@RequestBody @Valid OrderRequest request) {
        Order order = orderService.createOrder(request.reservationId(), request.skuId(), request.quantity());
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable UUID id) {
        Order order = orderService.getOrder(id);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<List<PaymentAttempt>> getOrderPaymentAttempts(
            @PathVariable UUID id,
            @RequestHeader("X-Tenant-Id") String tenantId) {
        List<PaymentAttempt> attempts = paymentAttemptRepository.findByOrderIdAndTenantId(id, tenantId);
        return ResponseEntity.ok(attempts);
    }
}