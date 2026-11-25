package com.example.notification_service.client;

import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import com.example.notification_service.common.web.ApiResponse;
import feign.FeignException;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class FeignIdentityGateway implements IdentityGateway {
    private static final Set<String> ACCOUNT_TYPES = Set.of("CUSTOMER", "SELLER", "ADMIN");
    private static final Set<String> ACCOUNT_STATUSES = Set.of("ACTIVE", "BANNED", "DISABLED");
    private static final Set<String> USER_STATUSES = Set.of("ACTIVE", "DISABLED");

    private final IdentityClient client;
    private final JsonMapper json;
    private final String serviceToken;

    public FeignIdentityGateway(IdentityClient client, JsonMapper json,
            @Value("${notification.internal.service-token:}") String serviceToken) {
        this.client = client;
        this.json = json;
        this.serviceToken = serviceToken;
    }

    @Override
    public AuthorizationContext authorizationContext(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        AuthorizationContext context = call(
                () -> client.authorizationContext("Bearer " + accessToken, serviceToken), true);
        if (context.userId() == null || !contains(ACCOUNT_TYPES, context.accountType())
                || !contains(ACCOUNT_STATUSES, context.accountStatus())
                || !contains(USER_STATUSES, context.userStatus()) || context.permissions() == null) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return context;
    }

    @Override
    public UserReference recipient(UUID userId) {
        UserReference user = call(() -> client.recipient(userId, serviceToken), false);
        if (!userId.equals(user.userId()) || !contains(ACCOUNT_TYPES, user.accountType())) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return user;
    }

    private <T> T call(Supplier<ApiResponse<T>> request, boolean authorization) {
        if (serviceToken == null || serviceToken.length() < 32 || serviceToken.isBlank()) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        ApiResponse<T> response;
        try {
            response = request.get();
        }
        catch (FeignException ex) {
            if (!authorization && ex.status() == 404) {
                throw new ApiException(ErrorCode.NOT_FOUND);
            }
            if (authorization && ex.status() == 401) {
                throw new ApiException(tokenError(ex));
            }
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        if (response == null || response.data() == null) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE);
        }
        return response.data();
    }

    private ErrorCode tokenError(FeignException exception) {
        try {
            String code = json.readTree(exception.contentUTF8()).path("error").path("code").asText();
            return switch (code) {
                case "UNAUTHENTICATED" -> ErrorCode.UNAUTHENTICATED;
                case "TOKEN_EXPIRED" -> ErrorCode.TOKEN_EXPIRED;
                case "TOKEN_STALE" -> ErrorCode.TOKEN_STALE;
                default -> ErrorCode.SERVICE_UNAVAILABLE;
            };
        }
        catch (RuntimeException ex) {
            return ErrorCode.SERVICE_UNAVAILABLE;
        }
    }

    private static boolean contains(Set<String> allowed, String value) {
        return value != null && allowed.contains(value);
    }
}
