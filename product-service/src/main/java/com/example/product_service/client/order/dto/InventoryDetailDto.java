package com.example.product_service.client.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryDetailDto {

    private UUID productId;

    private Integer onHandQuantity;

    private Integer reservedQuantity;

    private Integer availableQuantity;

    private Long version;

    private OffsetDateTime updatedAt;
}
