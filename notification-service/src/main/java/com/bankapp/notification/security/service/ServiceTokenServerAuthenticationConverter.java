package com.bankapp.notification.security.service;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// Reactive equivalent of JwtServerAuthenticationConverter, reading the dedicated X-Service-Token
// header instead of Authorization - keeps service-to-service auth structurally distinct from user
// bearer tokens.
@Component
public class ServiceTokenServerAuthenticationConverter implements ServerAuthenticationConverter {

    private static final String HEADER = "X-Service-Token";

    @Override
    public Mono<Authentication> convert(ServerWebExchange exchange) {
        String token = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (token == null || token.isBlank()) {
            return Mono.empty();
        }
        return Mono.just(new ServiceAuthenticationToken(token));
    }
}
