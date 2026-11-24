package com.example.payment_service.client;

import static com.example.payment_service.client.ProviderContracts.REFUND_ATTEMPT_NOT_FOUND;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.example.payment_service.client.ProviderContracts.CreatePaymentRequest;
import com.example.payment_service.client.ProviderContracts.CreatePaymentResponse;
import com.example.payment_service.client.ProviderContracts.CreateRefundRequest;
import com.example.payment_service.client.ProviderContracts.PaymentQueryResponse;
import com.example.payment_service.client.ProviderContracts.RefundAttemptAbsence;
import com.example.payment_service.client.ProviderContracts.RefundResponse;

public final class ProviderHttpGateway implements PaymentGateway {
    private final RestClient provider;

    public ProviderHttpGateway(URI origin, String credentialHeader, String credential, Duration timeout) {
        Objects.requireNonNull(origin, "origin");
        if (origin.getHost() == null || origin.getUserInfo() != null
                || origin.getQuery() != null || origin.getFragment() != null) {
            throw new IllegalArgumentException("Provider origin must be an HTTP origin");
        }
        if (!"https".equalsIgnoreCase(origin.getScheme()) && !isLoopbackHttp(origin)) {
            throw new IllegalArgumentException("Provider transport must use HTTPS outside loopback tests");
        }
        if (credentialHeader == null || credentialHeader.isBlank() || credential == null || credential.isBlank()) {
            throw new IllegalArgumentException("Provider service credential is required");
        }
        var requests = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build());
        requests.setReadTimeout(timeout);
        provider = RestClient.builder().baseUrl(origin.toString())
                .defaultHeader(credentialHeader, credential)
                .requestFactory(requests).build();
    }

    @Override
    public CreatePaymentResponse createPayment(CreatePaymentRequest request) {
        var response = exchange(provider.post().uri("/v1/payments")
                .contentType(MediaType.APPLICATION_JSON).body(request), CreatePaymentResponse.class);
        if (missing(response.providerTransactionId()) || response.status() == null
                || !request.expiresAt().equals(response.expiresAt())) {
            throw new UncertainResultException();
        }
        return response;
    }

    @Override
    public PaymentQueryResponse queryPayment(UUID paymentId) {
        var result = exchange(provider.get().uri("/v1/payments/{id}", paymentId), PaymentQueryResponse.class);
        if (missing(result.providerTransactionId()) || result.expiresAt() == null
                || !validResult(result.status(), result.amountVnd(), result.currency(),
                        result.completedAt(), result.errorCode())) {
            throw new UncertainResultException();
        }
        return result;
    }

    @Override
    public RefundResponse createRefund(CreateRefundRequest request) {
        var result = validateRefund(exchange(provider.post().uri("/v1/refunds")
                .contentType(MediaType.APPLICATION_JSON).body(request), RefundResponse.class));
        if (result.amountVnd() != request.amountVnd() || !request.currency().equals(result.currency())) {
            throw new UncertainResultException();
        }
        return result;
    }

    @Override
    public RefundResponse queryRefund(UUID refundId, UUID attemptId) {
        try {
            return validateRefund(provider.get().uri("/v1/refunds/{refundId}/attempts/{attemptId}", refundId, attemptId)
                    .accept(MediaType.APPLICATION_JSON).retrieve()
                    .onStatus(status -> !status.is2xxSuccessful() && !status.isError(), (sent, response) -> {
                        throw new ProviderHttpException(response.getStatusCode().value());
                    }).body(RefundResponse.class));
        } catch (RestClientResponseException failure) {
            if (failure.getStatusCode().value() == 404) {
                var absence = readAbsence(failure);
                if (absence != null && REFUND_ATTEMPT_NOT_FOUND.equals(absence.errorCode())
                        && refundId.equals(absence.refundId()) && attemptId.equals(absence.attemptId())
                        && !missing(absence.providerRefundId()) && absence.completedAt() != null) {
                    throw new RefundAttemptNotFound(absence);
                }
            }
            throw new ProviderHttpException(failure.getStatusCode().value());
        } catch (RestClientException failure) {
            throw new UncertainResultException();
        }
    }

    private static RefundResponse validateRefund(RefundResponse result) {
        if (result == null || missing(result.providerRefundId())
                || !validResult(result.status(), result.amountVnd(), result.currency(),
                        result.completedAt(), result.errorCode())) {
            throw new UncertainResultException();
        }
        return result;
    }

    private static RefundAttemptAbsence readAbsence(RestClientResponseException failure) {
        try {
            return failure.getResponseBodyAs(RefundAttemptAbsence.class);
        } catch (RestClientException invalidBody) {
            return null;
        }
    }

    private static <T> T exchange(RestClient.RequestHeadersSpec<?> request, Class<T> responseType) {
        try {
            T body = request.accept(MediaType.APPLICATION_JSON).retrieve()
                    .onStatus(status -> !status.is2xxSuccessful(), (sent, response) -> {
                        throw new ProviderHttpException(response.getStatusCode().value());
                    })
                    .body(responseType);
            if (body == null) {
                throw new UncertainResultException();
            }
            return body;
        } catch (RestClientResponseException e) {
            throw new ProviderHttpException(e.getStatusCode().value());
        } catch (RestClientException e) {
            throw new UncertainResultException();
        }
    }

    private static boolean validResult(GatewayStatus status, long amount, String currency,
            Instant completedAt, String errorCode) {
        if (status == null || amount <= 0 || !"VND".equals(currency)) {
            return false;
        }
        return switch (status) {
            case PENDING -> completedAt == null;
            case SUCCESS -> completedAt != null;
            case FAILED -> completedAt != null && !missing(errorCode);
        };
    }

    private static boolean isLoopbackHttp(URI origin) {
        return "http".equalsIgnoreCase(origin.getScheme())
                && ("127.0.0.1".equals(origin.getHost()) || "localhost".equalsIgnoreCase(origin.getHost())
                || "[::1]".equals(origin.getHost()));
    }

    private static boolean missing(String value) {
        return value == null || value.isBlank();
    }

    public static final class ProviderHttpException extends RuntimeException {
        private final int statusCode;

        private ProviderHttpException(int statusCode) {
            super("Provider HTTP request did not succeed: " + statusCode);
            this.statusCode = statusCode;
        }

        public int statusCode() {
            return statusCode;
        }
    }

    public static final class RefundAttemptNotFound extends RuntimeException {
        private final RefundAttemptAbsence absence;

        private RefundAttemptNotFound(RefundAttemptAbsence absence) {
            this.absence = absence;
        }

        public RefundAttemptAbsence absence() {
            return absence;
        }
    }

    public static final class UncertainResultException extends RuntimeException {
        private UncertainResultException() {
            super("Provider result could not be determined");
        }
    }
}
