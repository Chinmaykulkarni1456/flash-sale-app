package com.flashsale.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateProductRequest {
    @NotBlank
    private String sku;

    @NotBlank
    private String name;

    private String description;
}