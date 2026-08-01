package com.bankapp.notification.security.service;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.test.StepVerifier;

class ServiceTokenServerAuthenticationConverterTest {

    private final ServiceTokenServerAuthenticationConverter converter = new ServiceTokenServerAuthenticationConverter();

    @Test
    void convert_headerPresent_emitsUnauthenticatedTokenWrappingIt() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/internal/messages").header("X-Service-Token", "abc123"));

        StepVerifier.create(converter.convert(exchange))
                .assertNext(auth -> {
                    ServiceAuthenticationToken token = (ServiceAuthenticationToken) auth;
                    org.assertj.core.api.Assertions.assertThat(token.getCredentials()).isEqualTo("abc123");
                    org.assertj.core.api.Assertions.assertThat(token.isAuthenticated()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void convert_headerAbsent_emitsEmpty() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/internal/messages"));

        StepVerifier.create(converter.convert(exchange)).verifyComplete();
    }
}
