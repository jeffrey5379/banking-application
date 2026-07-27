package com.bankapp.client;

import com.bankapp.exception.KycNotVerifiedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.UUID;

// Calls identity-service's internal (unauthenticated, not gateway-routed) KYC endpoint to gate
// account creation and outgoing transfers on verification status. Same fail-closed shape as
// DebitEligibilityClient: if identity-service is slow/down/erroring, the operation is rejected
// rather than silently allowed.
@Component
public class KycStatusClient {

    private final RestClient restClient;

    public KycStatusClient(@Value("${kyc.service.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @CircuitBreaker(name = "kycStatus", fallbackMethod = "fallback")
    public void requireVerified(UUID userId) {
        EligibilityResponse response = restClient.get()
                .uri("/internal/kyc/{userId}/eligibility", userId)
                .retrieve()
                .body(EligibilityResponse.class);
        if (response == null || !"VERIFIED".equals(response.status())) {
            throw new KycNotVerifiedException("Identity verification required before this action is allowed");
        }
    }

    // Fail-closed: any failure (timeout, 5xx, open circuit) rejects the action
    @SuppressWarnings("unused")
    private void fallback(UUID userId, Throwable t) {
        throw new KycNotVerifiedException("Verification check temporarily unavailable, please try again later");
    }

    private record EligibilityResponse(UUID userId, String status, String level) {}
}
