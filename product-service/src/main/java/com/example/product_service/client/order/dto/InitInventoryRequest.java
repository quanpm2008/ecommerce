package com.example.product_service.client.order.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InitInventoryRequest {

    private UUID productId;

    private UUID sellerId;

    private Integer onHandQuantity;
}
