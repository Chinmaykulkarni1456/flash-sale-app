package com.flashsale.reservation.controller;

import com.flashsale.reservation.domain.Reservation;
import com.flashsale.reservation.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
public class ReservationController {

    private final ReservationService reservationService;

    public record CreateReservationRequest(
            @NotBlank String skuId,
            @Min(1) int quantity
    ) {}

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'ADMIN')")
    public ResponseEntity<Reservation> createReservation(
            @RequestHeader("Idempotency-Key") String idempotencyKey, // MANDATORY: Idempotency enforcement
            @RequestHeader("Authorization") String authHeader,
            @Valid @RequestBody CreateReservationRequest request) {

        Reservation reservation = reservationService.createReservation(
                request.skuId(),
                request.quantity(),
                idempotencyKey,
                authHeader
        );

        return ResponseEntity.ok(reservation);
    }
}