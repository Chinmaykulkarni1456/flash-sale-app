package com.flashsale.inventory.controller;

import com.flashsale.inventory.dto.*;
import com.flashsale.inventory.entity.InventoryDomainEvent;
import com.flashsale.inventory.entity.Product;
import com.flashsale.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final InventoryService inventoryService;

    @PostMapping("/products")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Product> createProduct(@Valid @RequestBody CreateProductRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inventoryService.createProduct(request));
    }

    @PostMapping("/stock")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<StockResponse> adjustStock(@Valid @RequestBody AdjustStockRequest request) {
        return ResponseEntity.ok(inventoryService.setOrAdjustStock(request));
    }

    @GetMapping("/products/{productId}/stock")
    public ResponseEntity<List<StockResponse>> getStock(@PathVariable String productId) {
        return ResponseEntity.ok(inventoryService.getStockForProduct(productId));
    }

    @GetMapping("/events")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InventoryDomainEvent>> getEvents() {
        return ResponseEntity.ok(inventoryService.getDomainEvents());
    }
}