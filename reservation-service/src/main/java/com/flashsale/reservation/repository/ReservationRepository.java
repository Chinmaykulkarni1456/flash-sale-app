package com.flashsale.reservation.repository;

import com.flashsale.reservation.domain.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    // MULTI-TENANCY: All database queries must enforce tenant isolation
    Optional<Reservation> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);

    Optional<Reservation> findByTenantIdAndId(String tenantId, Long id);

    // BATCH QUERY: Fetch active holds past their TTL for auto-release worker
    @Query("SELECT r FROM Reservation r WHERE r.status = 'ACTIVE' AND r.expiresAt < :now")
    List<Reservation> findExpiredReservations(Instant now);
}