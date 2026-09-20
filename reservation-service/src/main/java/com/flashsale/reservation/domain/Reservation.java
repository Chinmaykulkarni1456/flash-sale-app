package com.flashsale.reservation.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "reservations")
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tenantId;

    @Column(nullable = false)
    private String skuId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status; // ACTIVE, CONFIRMED, CANCELLED, EXPIRED

    @Column(nullable = false)
    private String idempotencyKey;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant createdAt;
    private Instant updatedAt;

    public enum ReservationStatus { ACTIVE, CONFIRMED, CANCELLED, EXPIRED }
}