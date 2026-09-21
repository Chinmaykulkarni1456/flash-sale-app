package com.flashsale.order.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderRequest(
            @NotNull
            UUID reservationId,

            @NotNull
            String skuId,

            @Min(1)
            int quantity
    ) {}