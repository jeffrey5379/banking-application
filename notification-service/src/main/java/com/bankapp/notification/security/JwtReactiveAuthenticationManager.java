package com.bankapp.notification.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class JwtReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final JwtService jwtService;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        String token = (String) authentication.getCredentials();
        return Mono.fromCallable(() -> {
            if (!jwtService.isTokenValid(token)) {
                throw new BadCredentialsException("Invalid or expired token");
            }
            return jwtService.extractUserId(token);
        }).map(userId -> (Authentication) new JwtAuthenticationToken(userId, token))
          .onErrorMap(ex -> !(ex instanceof BadCredentialsException),
                  ex -> new BadCredentialsException("Invalid or expired token", ex));
    }
}
