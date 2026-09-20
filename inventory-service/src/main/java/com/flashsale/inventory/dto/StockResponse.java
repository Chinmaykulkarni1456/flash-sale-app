package com.flashsale.inventory.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StockResponse {
    private String productId;
    private String warehouseId;
    private int onHand;
    private int reserved;
    private int available;
}