package com.flashsale.order.repository;

import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Optional<Order> findByIdAndTenantId(UUID id, String tenantId);
    Optional<Order> findByPaymentTransactionId(String paymentTransactionId);
    List<Order> findByStatusAndCreatedAtBefore(OrderStatus status, Instant createdAt);
}