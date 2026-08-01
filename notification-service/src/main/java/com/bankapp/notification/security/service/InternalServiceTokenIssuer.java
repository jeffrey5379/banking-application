package com.bankapp.notification.security.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Duration;
import java.util.Date;

@Component
public class InternalServiceTokenIssuer {

    private static final String ISSUER_KID = "notification-service";
    private static final Duration TOKEN_TTL = Duration.ofSeconds(60);

    @Value("${service.jwt.secret}")
    private String secret;

    private SecretKey signingKey;

    @PostConstruct
    private void init() {
        signingKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String mintToken() {
        Date now = new Date();
        return Jwts.builder()
                .header().add("kid", ISSUER_KID).and()
                .claim("scope", "internal")
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TOKEN_TTL.toMillis()))
                .signWith(signingKey)
                .compact();
    }
}
