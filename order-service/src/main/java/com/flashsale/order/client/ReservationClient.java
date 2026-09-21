package com.flashsale.order.client;

import com.flashsale.commons.context.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.UUID;

@Component
public class ReservationClient {

    private final RestClient restClient;

    public ReservationClient(RestClient.Builder builder, @Value("${reservation.service.url:http://localhost:8082}") String baseUrl) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build(); // Removed invalid defaultHeader
    }

    public record ReservationDto(UUID id, String skuId, int quantity, String status, Instant expiresAt) {}

    public ReservationDto getReservation(UUID reservationId) {
        return restClient.get()
                .uri("/api/v1/reservations/{id}", reservationId)
                .header("X-Tenant-Id", TenantContext.getTenantId()) // Injected dynamically per request
                .retrieve()
                .body(ReservationDto.class);
    }

    public void confirmReservation(UUID reservationId) {
        restClient.post()
                .uri("/api/v1/internal/reservations/{id}/confirm", reservationId)
                .header("X-Tenant-Id", TenantContext.getTenantId()) // Injected dynamically per request
                .retrieve()
                .toBodilessEntity();
    }

    public void cancelReservation(UUID reservationId) {
        restClient.post()
                .uri("/api/v1/internal/reservations/{id}/cancel", reservationId)
                .header("X-Tenant-Id", TenantContext.getTenantId()) // Injected dynamically per request
                .retrieve()
                .toBodilessEntity();
    }
}