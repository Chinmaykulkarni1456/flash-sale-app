package com.flashsale.reservation.worker;

import com.flashsale.commons.context.TenantContext;
import com.flashsale.reservation.client.InventoryClient;
import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.domain.Reservation.ReservationStatus;
import com.flashsale.reservation.repository.ReservationRepository;
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
public class ReservationExpiryScheduler {

    private final ReservationRepository reservationRepository;
    private final InventoryClient inventoryClient;

    // BACKGROUND WORKER: Runs every 10 seconds to auto-expire holds past their TTL
    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void processExpiredReservations() {
        List<Reservation> expiredReservations = reservationRepository.findExpiredReservations(Instant.now());

        for (Reservation reservation : expiredReservations) {
            try {
                // CRITICAL: Bind tenant context to the background thread for downstream REST calls
                TenantContext.setTenantId(reservation.getTenantId());

                log.info("Expiring reservation ID: {} for tenant: {}", reservation.getId(), reservation.getTenantId());

                // STEP 1: Mark status as EXPIRED
                reservation.setStatus(ReservationStatus.EXPIRED);
                reservation.setUpdatedAt(Instant.now());
                reservationRepository.save(reservation);

                // STEP 2: Notify inventory-service to release held stock back to available pool
                inventoryClient.releaseStock(
                        reservation.getSkuId(),
                        reservation.getQuantity(),
                        "System-Background-Worker"
                );
            } catch (Exception e) {
                log.error("Failed to process expiration for reservation {}: {}", reservation.getId(), e.getMessage());
            } finally {
                // Always clear context to prevent thread-pool leakage
                TenantContext.clear();
            }
        }
    }
}