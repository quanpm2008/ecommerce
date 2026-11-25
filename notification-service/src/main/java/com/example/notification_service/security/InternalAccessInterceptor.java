package com.example.notification_service.security;

import com.example.notification_service.common.error.ApiException;
import com.example.notification_service.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class InternalAccessInterceptor implements HandlerInterceptor {
    public static final String PRODUCER_ATTRIBUTE = "notification.authenticatedProducer";
    private final ServiceCredentials credentials;

    public InternalAccessInterceptor(ServiceCredentials credentials) {
        this.credentials = credentials;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String producer = credentials.serviceFor(request.getHeader("X-Service-Token"))
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHENTICATED));
        request.setAttribute(PRODUCER_ATTRIBUTE, producer);
        return true;
    }
}
