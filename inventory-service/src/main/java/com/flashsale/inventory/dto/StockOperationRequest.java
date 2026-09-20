package com.flashsale.inventory.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class StockOperationRequest {
    @NotBlank
    private String productId;

    @NotBlank
    private String warehouseId;

    @Min(1)
    private int quantity;
}