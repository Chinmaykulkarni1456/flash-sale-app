package com.flashsale.reservation.service;

import com.flashsale.commons.context.TenantContext;
import com.flashsale.commons.exception.BusinessException;
import com.flashsale.reservation.ReservationService;
import com.flashsale.reservation.client.InventoryClient;
import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.repository.ReservationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private ReservationRepository reservationRepository;

    @MockBean
    private InventoryClient inventoryClient; // MOCK PATTERN: Isolate downstream inventory call dependency

    @BeforeEach
    void setUp() {
        TenantContext.setTenantId("tenant-alpha");
    }

    @AfterEach
    void tearDown() {
        reservationRepository.deleteAll();
        TenantContext.clear();
    }

    @Test
    @DisplayName("Idempotency Guard: Identical key must return existing reservation without re-allocating")
    void testIdempotentReservation() {
        String idempotencyKey = "key-12345";

        // Mock successful downstream allocation
        doNothing().when(inventoryClient).allocateStock(anyString(), anyInt(), anyString());

        // First execution
        Reservation firstCall = reservationService.createReservation("SKU-001", 2, idempotencyKey, "Bearer token");

        // Second execution with identical Idempotency Key
        Reservation secondCall = reservationService.createReservation("SKU-001", 2, idempotencyKey, "Bearer token");

        // TEST VERIFICATION: Entities must be identical and downstream inventory called exactly once
        assertEquals(firstCall.getId(), secondCall.getId());
        verify(inventoryClient, times(1)).allocateStock(anyString(), anyInt(), anyString());
    }

    @Test
    @DisplayName("Fail-Safe: Downstream allocation failure must not persist an active reservation")
    void testStockAllocationFailureRollback() {
        String idempotencyKey = "key-failed-alloc";

        // MOCK BEHAVIOR: Simulate downstream inventory HTTP exception
        doThrow(new BusinessException("STOCK_ALLOCATION_FAILED", "No stock", org.springframework.http.HttpStatus.BAD_REQUEST))
                .when(inventoryClient).allocateStock(anyString(), anyInt(), anyString());

        // Expect BusinessException and verify DB remains empty
        assertThrows(BusinessException.class, () ->
                reservationService.createReservation("SKU-001", 5, idempotencyKey, "Bearer token")
        );

        assertTrue(reservationRepository.findByTenantIdAndIdempotencyKey("tenant-alpha", idempotencyKey).isEmpty()); // FAIL-SAFE CHECK
    }
}