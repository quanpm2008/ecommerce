package com.example.notification_service.client;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Identity is the live authority for user sessions, permissions and recipient accounts. */
public interface IdentityGateway {
    AuthorizationContext authorizationContext(String accessToken);

    UserReference recipient(UUID userId);

    record UserReference(UUID userId, String accountType) {}

    record AuthorizationContext(UUID userId, String accountType, String accountStatus, String userStatus,
            String accessMode, Instant bannedAt, Instant disabledAt, Instant restoreUntil, long authzVersion,
            List<String> roles, List<String> permissions, CustomerAccess customerProfile,
            SellerAccess sellerProfile, boolean sessionActive) {}

    record CustomerAccess(String status, boolean paymentBlocked) {}

    record SellerAccess(String approvalStatus, String status, boolean canOperate) {}
}
