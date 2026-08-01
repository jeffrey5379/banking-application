package com.bankapp.notification.security.service;

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

// Resolves which HMAC secret to verify an incoming X-Service-Token with, based on the JWS
// header's "kid" (the calling service's name) - see InternalServiceAuthFilter. Only core-banking
// is a trusted caller of this service's own /internal/messages endpoint today.
@Component
public class ServiceTokenKeyLocator extends LocatorAdapter<Key> {

    private final Map<String, SecretKey> trustedKeysByIssuer = new ConcurrentHashMap<>();

    @Value("${service.jwt.trusted-issuers.core-banking:}")
    private String coreBankingSecret;

    @PostConstruct
    private void init() {
        if (coreBankingSecret != null && !coreBankingSecret.isBlank()) {
            trustedKeysByIssuer.put("core-banking", Keys.hmacShaKeyFor(Decoders.BASE64.decode(coreBankingSecret)));
        }
    }

    @Override
    protected Key locate(JwsHeader header) {
        return trustedKeysByIssuer.get(header.getKeyId());
    }
}
