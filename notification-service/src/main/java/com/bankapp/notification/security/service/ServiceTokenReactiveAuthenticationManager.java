package com.bankapp.notification.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// Reactive equivalent of JwtReactiveAuthenticationManager, for the internal-service-only
// SecurityWebFilterChain (see SecurityConfig's internalServiceWebFilterChain).
@Component
@RequiredArgsConstructor
public class ServiceTokenReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final ServiceTokenAuthenticator authenticator;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        return Mono.fromCallable(() -> authenticator.verify(token)
                        .orElseThrow(() -> new BadCredentialsException("Invalid or untrusted service token")))
                .map(issuer -> (Authentication) new ServiceAuthenticationToken(issuer, token));
    }
}
