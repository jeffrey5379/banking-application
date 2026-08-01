package com.bankapp.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ServiceTokenAuthenticator {

    private static final String INTERNAL_SCOPE = "internal";

    private final ServiceTokenKeyLocator keyLocator;

    // Returns the verified caller (service) name, or empty if the token is missing, invalid,
    // expired, signed by an untrusted issuer, or not scoped for internal service-to-service use.
    public Optional<String> verify(String token) {
        try {
            Jws<Claims> jws = Jwts.parser()
                    .keyLocator(keyLocator)
                    .build()
                    .parseSignedClaims(token);
            Claims claims = jws.getPayload();
            if (!INTERNAL_SCOPE.equals(claims.get("scope", String.class))) {
                return Optional.empty();
            }
            return Optional.ofNullable(jws.getHeader().getKeyId());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
