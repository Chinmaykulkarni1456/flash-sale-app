package com.flashsale.order.client;

import com.flashsale.commons.context.TenantContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InventoryClient {

    private final RestClient restClient;

    public InventoryClient(RestClient.Builder builder, @Value("${inventory.service.url:http://localhost:8081}") String baseUrl) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build(); // Removed invalid defaultHeader
    }

    public record StockRequest(String skuId, int quantity) {}

    public void commitStock(String skuId, int quantity) {
        restClient.post()
                .uri("/api/v1/internal/stock/commit")
                .header("X-Tenant-Id", TenantContext.getTenantId()) // Injected dynamically per request
                .body(new StockRequest(skuId, quantity))
                .retrieve()
                .toBodilessEntity();
    }

    public void releaseStock(String skuId, int quantity) {
        restClient.post()
                .uri("/api/v1/internal/stock/release")
                .header("X-Tenant-Id", TenantContext.getTenantId()) // Injected dynamically per request
                .body(new StockRequest(skuId, quantity))
                .retrieve()
                .toBodilessEntity();
    }
}