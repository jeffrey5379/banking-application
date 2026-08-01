package com.bankapp.client;

import com.bankapp.exception.DebitNotAllowedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Component
public class DebitEligibilityClient {

    private final RestClient restClient;

    public DebitEligibilityClient(@Value("${debit.eligibility.url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(3));

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }

    @CircuitBreaker(name = "debitEligibility", fallbackMethod = "fallback")
    public boolean isDebitAllowed(Long userId) {
        EligibilityResponse response = restClient.get()
                .uri("/debit-eligibility/{userId}", userId)
                .retrieve()
                .body(EligibilityResponse.class);
        return response != null && response.isDebitAllowedForUser();
    }

    // Fail-closed: any failure (timeout, 5xx, open circuit) rejects the debit
    @SuppressWarnings("unused")
    private boolean fallback(Long userId, Throwable t) {
        throw new DebitNotAllowedException("Operation temporarily unavailable, please try again later");
    }

    private record EligibilityResponse(boolean isDebitAllowedForUser) {}
}
