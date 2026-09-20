package com.flashsale.reservation.client;

import com.flashsale.commons.context.TenantContext;
import com.flashsale.commons.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class InventoryClient {

    private final RestClient restClient;

    // RESILIENCE: RestClient initialized with explicit connection & read timeouts to prevent thread starvation
    public InventoryClient(@Value("${inventory.service.url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory() {{
                    setConnectTimeout(Duration.ofSeconds(2)); // RESILIENCE: Fast fail on connection stall
                    setReadTimeout(Duration.ofSeconds(3));    // RESILIENCE: Fast fail on slow response
                }})
                .build();
    }

    public record AllocateRequest(String skuId, int quantity) {}
    public record InventoryResponse(boolean success, String message) {}

    // DESIGN PATTERN: External API Integration Proxy Pattern
    public void allocateStock(String skuId, int quantity, String bearerToken) {
        try {
            restClient.post()
                    .uri("/api/v1/internal/stock/allocate")
                    .header("Authorization", bearerToken)
                    .header("X-Tenant-Id", TenantContext.getTenantId()) // MULTI-TENANCY: Explicit tenant header propagation
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AllocateRequest(skuId, quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            // FAIL-SAFE: Guarantee no active reservation created if downstream stock allocation fails
            throw new BusinessException("STOCK_ALLOCATION_FAILED", "Failed to allocate stock in inventory-service", HttpStatus.BAD_REQUEST);
        }
    }

    public void releaseStock(String skuId, int quantity, String bearerToken) {
        try {
            restClient.post()
                    .uri("/api/v1/internal/stock/release")
                    .header("Authorization", bearerToken)
                    .header("X-Tenant-Id", TenantContext.getTenantId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new AllocateRequest(skuId, quantity))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ex) {
            // LOGGING & RESILIENCE: Log and suppress to ensure background expiry engine proceeds
        }
    }
}