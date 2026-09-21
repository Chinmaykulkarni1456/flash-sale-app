package com.flashsale.order.worker;

import com.flashsale.commons.context.TenantContext;
import com.flashsale.order.domain.Order;
import com.flashsale.order.domain.OrderStatus;
import com.flashsale.order.repository.OrderRepository;
import com.flashsale.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderReconciliationScheduler {

    private final OrderRepository orderRepository;
    private final OrderService orderService;

    // Runs every 60 seconds to catch orders stuck in PENDING_PAYMENT
    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void reconcileStaleOrders() {
        // Threshold: orders older than 3 minutes (180 seconds) still pending payment
        Instant threshold = Instant.now().minusSeconds(180);
        List<Order> stuckOrders = orderRepository.findByStatusAndCreatedAtBefore(OrderStatus.PENDING_PAYMENT, threshold);

        if (stuckOrders.isEmpty()) {
            return;
        }

        log.info("Found {} stale orders stuck in PENDING_PAYMENT. Starting reconciliation sweep...", stuckOrders.size());

        for (Order order : stuckOrders) {
            try {
                // CRITICAL: Bind tenant context so Feign/RestClient calls pass the correct tenant header
                TenantContext.setTenantId(order.getTenantId());

                orderService.timeoutOrder(order.getId());

            } catch (Exception e) {
                log.error("Failed to reconcile stale order {}: {}", order.getId(), e.getMessage());
            } finally {
                TenantContext.clear();
            }
        }
    }
}