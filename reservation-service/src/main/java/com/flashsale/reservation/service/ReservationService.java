package com.flashsale.reservation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commons.context.TenantContext;
import com.flashsale.reservation.client.InventoryClient;
import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.domain.Reservation.ReservationStatus;
import com.flashsale.reservation.repository.ReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final InventoryClient inventoryClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_TTL_MINUTES = 10;

    @Transactional
    public Reservation createReservation(String skuId, int quantity, String idempotencyKey, String authHeader) {
        String tenantId = TenantContext.getTenantId();

        // IDEMPOTENCY GUARD: Short-circuit check before calling downstream inventory service
        Optional<Reservation> existing = reservationRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (existing.isPresent()) {
            return existing.get(); // IDEMPOTENCY: Replay identical response without re-allocating
        }

        // STEP 1: Allocate physical inventory in inventory-service
        inventoryClient.allocateStock(skuId, quantity, authHeader);

        // STEP 2: Persist reservation hold with TTL
        Reservation reservation = Reservation.builder()
                .tenantId(tenantId)
                .skuId(skuId)
                .quantity(quantity)
                .status(ReservationStatus.ACTIVE)
                .idempotencyKey(idempotencyKey)
                .expiresAt(Instant.now().plus(DEFAULT_TTL_MINUTES, ChronoUnit.MINUTES))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        try {
            reservation = reservationRepository.save(reservation);
        } catch (DataIntegrityViolationException ex) {
            // CONCURRENCY GUARD: Catch race conditions where two identical idempotency keys passed early check
            return reservationRepository.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey)
                    .orElseThrow(() -> ex);
        }

        // DESIGN PATTERN: Transactional Outbox Lite — Persist event in same DB transaction
        recordDomainEvent(tenantId, "StockReserved", Map.of(
                "reservationId", reservation.getId(),
                "skuId", skuId,
                "quantity", quantity,
                "expiresAt", reservation.getExpiresAt().toString()
        ));

        return reservation;
    }

    private void recordDomainEvent(String tenantId, String eventType, Object payload) {
        try {
            jdbcTemplate.update(
                    "INSERT INTO domain_events (tenant_id, event_type, payload) VALUES (?, ?, ?)",
                    tenantId, eventType, objectMapper.writeValueAsString(payload)
            );
        } catch (Exception ignored) {}
    }
}