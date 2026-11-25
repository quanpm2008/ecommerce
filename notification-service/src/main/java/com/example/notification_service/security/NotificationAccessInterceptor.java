package com.example.notification_service.security;

import com.example.notification_service.client.IdentityGateway;
import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.servlet.HandlerInterceptor;

/** Rechecks the live session and Identity's current BANNED allowlist for every public request. */
@Component
public class NotificationAccessInterceptor implements HandlerInterceptor {
    public static final String USER_ATTRIBUTE = "notification.authenticatedUserId";
    private static final String PERMISSION = "notification:read:own";
    private final IdentityGateway identity;

    public NotificationAccessInterceptor(@Lazy IdentityGateway identity) {
        this.identity = identity;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String token = bearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        request.setAttribute(USER_ATTRIBUTE, authorize(identity.authorizationContext(token)));
        return true;
    }

    private static UUID authorize(IdentityGateway.AuthorizationContext context) {
        if (context == null || context.userId() == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        if ("DISABLED".equals(context.userStatus()) || "DISABLED".equals(context.accountStatus())) {
            throw new ApiException(ErrorCode.ACCOUNT_DISABLED);
        }
        if (!context.sessionActive()) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        if (!"ACTIVE".equals(context.userStatus())
                || !("ACTIVE".equals(context.accountStatus()) || "BANNED".equals(context.accountStatus()))) {
            throw new ApiException(ErrorCode.FORBIDDEN);
        }
        if (context.permissions() == null || !context.permissions().contains(PERMISSION)) {
            throw new ApiException("BANNED".equals(context.accountStatus())
                    ? ErrorCode.ACCOUNT_BANNED : ErrorCode.FORBIDDEN);
        }
        return context.userId();
    }

    private static String bearerToken(String authorization) {
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        String token = authorization.substring(7).strip();
        if (token.isEmpty() || token.chars().anyMatch(Character::isWhitespace)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        return token;
    }
}
