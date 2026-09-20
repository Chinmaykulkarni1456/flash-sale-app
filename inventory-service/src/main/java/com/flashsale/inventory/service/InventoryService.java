package com.flashsale.inventory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashsale.commons.context.TenantContext;
import com.flashsale.commons.exception.BusinessException;
import com.flashsale.inventory.dto.*;
import com.flashsale.inventory.entity.*;
import com.flashsale.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;
    private final WarehouseStockRepository stockRepository;
    private final InventoryDomainEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public Product createProduct(CreateProductRequest request) {
        String tenantId = getRequiredTenant();

        productRepository.findByTenantIdAndSku(tenantId, request.getSku())
                .ifPresent(p -> {
                    throw new BusinessException(
                            "DUPLICATE_SKU",
                            "Product Creation Failed",
                            "Product with SKU " + request.getSku() + " already exists for this tenant",
                            HttpStatus.CONFLICT
                    );
                });

        Product product = Product.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .sku(request.getSku())
                .name(request.getName())
                .description(request.getDescription())
                .build();

        return productRepository.save(product);
    }

    @Transactional
    public StockResponse setOrAdjustStock(AdjustStockRequest request) {
        String tenantId = getRequiredTenant();

        productRepository.findByTenantIdAndId(tenantId, request.getProductId())
                .orElseThrow(() -> new BusinessException(
                        "PRODUCT_NOT_FOUND",
                        "Stock Adjustment Failed",
                        "Product not found: " + request.getProductId(),
                        HttpStatus.NOT_FOUND
                ));

        WarehouseStock stock = stockRepository.findByTenantIdAndProductIdAndWarehouseId(
                tenantId, request.getProductId(), request.getWarehouseId()
        ).orElseGet(() -> WarehouseStock.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .productId(request.getProductId())
                .warehouseId(request.getWarehouseId())
                .onHand(0)
                .reserved(0)
                .build());

        if (request.getOnHand() < stock.getReserved()) {
            throw new BusinessException(
                    "INVALID_STOCK_ADJUSTMENT",
                    "Stock Adjustment Failed",
                    "Cannot set on_hand (" + request.getOnHand() + ") lower than currently reserved stock (" + stock.getReserved() + ")",
                    HttpStatus.BAD_REQUEST
            );
        }

        stock.setOnHand(request.getOnHand());
        WarehouseStock saved = stockRepository.save(stock);

        return StockResponse.builder()
                .productId(saved.getProductId())
                .warehouseId(saved.getWarehouseId())
                .onHand(saved.getOnHand())
                .reserved(saved.getReserved())
                .available(saved.getAvailable())
                .build();
    }

    @Transactional(readOnly = true)
    public List<StockResponse> getStockForProduct(String productId) {
        String tenantId = getRequiredTenant();
        return stockRepository.findByTenantIdAndProductId(tenantId, productId)
                .stream()
                .map(s -> StockResponse.builder()
                        .productId(s.getProductId())
                        .warehouseId(s.getWarehouseId())
                        .onHand(s.getOnHand())
                        .reserved(s.getReserved())
                        .available(s.getAvailable())
                        .build())
                .toList();
    }

    @Transactional
    public void allocateStock(StockOperationRequest request) {
        String tenantId = getRequiredTenant();

        int updated = stockRepository.allocateStockAtomic(
                tenantId, request.getProductId(), request.getWarehouseId(), request.getQuantity()
        );

        if (updated == 0) {
            throw new BusinessException(
                    "INSUFFICIENT_STOCK",
                    "Stock Allocation Failed",
                    "Insufficient available stock for requested quantity: " + request.getQuantity(),
                    HttpStatus.CONFLICT
            );
        }

        saveEvent(tenantId, "StockReserved", request.getProductId(), request);
    }

    @Transactional
    public void releaseStock(StockOperationRequest request) {
        String tenantId = getRequiredTenant();

        int updated = stockRepository.releaseStockAtomic(
                tenantId, request.getProductId(), request.getWarehouseId(), request.getQuantity()
        );

        if (updated == 0) {
            throw new BusinessException(
                    "INVALID_RELEASE",
                    "Stock Release Failed",
                    "Cannot release more stock than currently reserved",
                    HttpStatus.BAD_REQUEST
            );
        }

        saveEvent(tenantId, "StockReleased", request.getProductId(), request);
    }

    @Transactional
    public void commitStock(StockOperationRequest request) {
        String tenantId = getRequiredTenant();

        int updated = stockRepository.commitStockAtomic(
                tenantId, request.getProductId(), request.getWarehouseId(), request.getQuantity()
        );

        if (updated == 0) {
            throw new BusinessException(
                    "INVALID_COMMIT",
                    "Stock Commit Failed",
                    "Cannot commit stock due to invalid reserved or on-hand balance",
                    HttpStatus.BAD_REQUEST
            );
        }

        saveEvent(tenantId, "StockCommitted", request.getProductId(), request);
    }

    @Transactional(readOnly = true)
    public List<InventoryDomainEvent> getDomainEvents() {
        String tenantId = getRequiredTenant();
        return eventRepository.findByTenantIdOrderByCreatedAtDesc(tenantId);
    }

    private String getRequiredTenant() {
        String tenantId = TenantContext.getTenantId();
        if (tenantId == null || tenantId.isBlank()) {
            throw new BusinessException(
                    "MISSING_TENANT",
                    "Access Denied",
                    "Tenant identity is missing from context",
                    HttpStatus.FORBIDDEN
            );
        }
        return tenantId;
    }

    @SneakyThrows
    private void saveEvent(String tenantId, String type, String aggregateId, Object payload) {
        InventoryDomainEvent event = InventoryDomainEvent.builder()
                .id(UUID.randomUUID().toString())
                .tenantId(tenantId)
                .eventType(type)
                .aggregateId(aggregateId)
                .payload(objectMapper.writeValueAsString(payload))
                .build();
        eventRepository.save(event);
    }
}