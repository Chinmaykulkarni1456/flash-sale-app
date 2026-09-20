package com.flashsale.reservation.worker;

import com.flashsale.commons.context.TenantContext;
import com.flashsale.reservation.client.InventoryClient;
import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.domain.Reservation.ReservationStatus;
import com.flashsale.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
class ReservationExpirySchedulerTest {

    @Autowired
    private ReservationRepository reservationRepository;

    @MockBean
    private InventoryClient inventoryClient;

    private ReservationExpiryScheduler expiryScheduler;

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-alpha");
        expiryScheduler = new ReservationExpiryScheduler(reservationRepository, inventoryClient);
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Background Worker: Expired holds must change status to EXPIRED and trigger inventory release")
    void testBackgroundExpiryWorker() {
        Reservation expiredHold = Reservation.builder()
                .tenantId("tenant-alpha")
                .skuId("SKU-EXPIRE")
                .quantity(3)
                .status(ReservationStatus.ACTIVE)
                .idempotencyKey("key-expired")
                .expiresAt(Instant.now().minus(5, ChronoUnit.MINUTES))
                .createdAt(Instant.now().minus(15, ChronoUnit.MINUTES))
                .build();

        expiredHold = reservationRepository.save(expiredHold);

        expiryScheduler.processExpiredReservations();

        Reservation updated = reservationRepository.findById(expiredHold.getId()).orElseThrow();
        assertEquals(ReservationStatus.EXPIRED, updated.getStatus());
        verify(inventoryClient).releaseStock(eq("SKU-EXPIRE"), eq(3), anyString());
    }
}