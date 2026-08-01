package com.bankapp.notification.security.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class ServiceTokenAuthenticatorTest {

    private static final String CORE_BANKING_SECRET =
            "zacTVpF/cO45cVpOAYTHbF0Rzntz1viiftAiVgBDJhDlRuWIfazQce/nKEJBDc5f";
    private static final String UNTRUSTED_SECRET =
            "cAgOnKGxdIWs5r8mQ0m2X9Zt1UoVqRnP3bYhLdEfJkTz7wCsAxNiRmVbOeGpUyIq";

    private ServiceTokenAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        ServiceTokenKeyLocator keyLocator = new ServiceTokenKeyLocator();
        ReflectionTestUtils.setField(keyLocator, "coreBankingSecret", CORE_BANKING_SECRET);
        ReflectionTestUtils.invokeMethod(keyLocator, "init");

        authenticator = new ServiceTokenAuthenticator(keyLocator);
    }

    private String token(String kid, SecretKey key, String scope, long expiresInMillis) {
        Date now = new Date();
        return Jwts.builder()
                .header().add("kid", kid).and()
                .claim("scope", scope)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiresInMillis))
                .signWith(key)
                .compact();
    }

    private SecretKey keyFor(String base64Secret) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(base64Secret));
    }

    @Test
    void verify_validCoreBankingToken_returnsIssuerName() {
        String token = token("core-banking", keyFor(CORE_BANKING_SECRET), "internal", 60_000);

        assertThat(authenticator.verify(token)).contains("core-banking");
    }

    @Test
    void verify_untrustedIssuer_returnsEmpty() {
        // notification-service is only configured to trust core-banking for its own
        // /internal/messages - itself is not a valid caller of its own endpoint.
        String token = token("notification-service", keyFor(UNTRUSTED_SECRET), "internal", 60_000);

        assertThat(authenticator.verify(token)).isEmpty();
    }

    @Test
    void verify_expiredToken_returnsEmpty() {
        String token = token("core-banking", keyFor(CORE_BANKING_SECRET), "internal", -1_000);

        assertThat(authenticator.verify(token)).isEmpty();
    }

    @Test
    void verify_wrongScope_returnsEmpty() {
        String token = token("core-banking", keyFor(CORE_BANKING_SECRET), "user", 60_000);

        assertThat(authenticator.verify(token)).isEmpty();
    }

    @Test
    void verify_malformedToken_returnsEmpty() {
        assertThat(authenticator.verify("not-a-jwt")).isEmpty();
    }
}
