package com.flashsale.inventory.controller;

import com.flashsale.inventory.dto.StockOperationRequest;
import com.flashsale.inventory.service.InventoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/internal/stock")
@RequiredArgsConstructor
public class InternalStockController {

    private final InventoryService inventoryService;

    @PostMapping("/allocate")
    public ResponseEntity<Void> allocate(@Valid @RequestBody StockOperationRequest request) {
        inventoryService.allocateStock(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@Valid @RequestBody StockOperationRequest request) {
        inventoryService.releaseStock(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/commit")
    public ResponseEntity<Void> commit(@Valid @RequestBody StockOperationRequest request) {
        inventoryService.commitStock(request);
        return ResponseEntity.ok().build();
    }
}