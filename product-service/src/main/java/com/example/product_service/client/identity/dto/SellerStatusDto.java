package com.example.product_service.client.identity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SellerStatusDto {

    private UUID sellerId;

    private UUID userId;

    private String userStatus;      // ACTIVE / DISABLED

    private String approvalStatus;  // PENDING / APPROVED / REJECTED

    private String status;          // ACTIVE / BANNED

    private Boolean sellerRoleActive;

    private Boolean sellable;
}
