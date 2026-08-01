package com.bankapp.security.service;

import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.LocatorAdapter;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.security.Key;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// Resolves which HMAC secret to verify an incoming service-to-service token with, based on the
// JWS header's "kid" (the calling service's name) - see InternalServiceAuthFilter. Each trusted
// caller gets its own secret, distributed only to the parties that legitimately need to verify
// it - unlike the shared jwt.secret used for *user* tokens, a leaked secret here only lets an
// attacker impersonate the one service it belongs to.
@Component
public class ServiceTokenKeyLocator extends LocatorAdapter<Key> {

    private final Map<String, SecretKey> trustedKeysByIssuer = new ConcurrentHashMap<>();

    @Value("${service.jwt.trusted-issuers.core-banking:}")
    private String coreBankingSecret;

    @Value("${service.jwt.trusted-issuers.notification-service:}")
    private String notificationSecret;

    @PostConstruct
    private void init() {
        register("core-banking", coreBankingSecret);
        register("notification-service", notificationSecret);
    }

    private void register(String issuer, String base64Secret) {
        if (base64Secret == null || base64Secret.isBlank()) {
            return;
        }
        trustedKeysByIssuer.put(issuer, Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret)));
    }

    @Override
    protected Key locate(JwsHeader header) {
        return trustedKeysByIssuer.get(header.getKeyId());
    }
}
