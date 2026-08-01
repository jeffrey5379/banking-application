package com.bankapp.notification.security;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

// @Primary: Spring Boot's reactive security autoconfiguration builds an internal ServerHttpSecurity
// prototype bean (the "http" parameter both SecurityConfig chain methods receive) that needs
// exactly one ReactiveAuthenticationManager to seed its defaults - with ServiceTokenReactiveAuthenticationManager
// also present, that autowiring is otherwise ambiguous. Both chains still always specify their own
// manager explicitly to their AuthenticationWebFilter, so this @Primary never actually affects
// which manager authenticates a given request - it only resolves Spring's own internal wiring.
@Component
@Primary
@RequiredArgsConstructor
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtService jwtService;
    private final AccountRevocationStore accountRevocationStore;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        return Mono.fromCallable(() -> {
            if (!jwtService.isTokenValid(token)) {
                throw new BadCredentialsException("Invalid or expired token");
            }
            return jwtService.extractUsername(token);
        }).flatMap(username -> accountRevocationStore.isRevoked(username, jwtService.extractIssuedAt(token))
                .flatMap(revoked -> revoked
                        ? Mono.<UUID>error(new BadCredentialsException("Account access revoked"))
                        : Mono.just(jwtService.extractUserId(token))))
          .map(userId -> (Authentication) new JwtAuthenticationToken(userId, token))
          .onErrorMap(ex -> !(ex instanceof BadCredentialsException),
                  ex -> new BadCredentialsException("Invalid or expired token", ex));
    }
}
