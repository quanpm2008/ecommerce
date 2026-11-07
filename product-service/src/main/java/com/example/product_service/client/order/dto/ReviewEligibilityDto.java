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
public class ReviewEligibilityDto {

    private Boolean eligible;

    private UUID productId;

    private String orderStatus;
}
