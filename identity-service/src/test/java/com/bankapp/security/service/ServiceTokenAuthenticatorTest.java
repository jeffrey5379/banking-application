package com.bankapp.security.service;

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
    private static final String NOTIFICATION_SECRET =
            "fB2RHu5YPOXLoP7QaKL4ivQ0bmLUExu+LoZ/vMn5qqEY5j+W4tOAtyQCIq/+E3ol";
    private static final String UNTRUSTED_SECRET =
            "cAgOnKGxdIWs5r8mQ0m2X9Zt1UoVqRnP3bYhLdEfJkTz7wCsAxNiRmVbOeGpUyIq";

    private ServiceTokenKeyLocator keyLocator;
    private ServiceTokenAuthenticator authenticator;

    @BeforeEach
    void setUp() {
        keyLocator = new ServiceTokenKeyLocator();
        ReflectionTestUtils.setField(keyLocator, "coreBankingSecret", CORE_BANKING_SECRET);
        ReflectionTestUtils.setField(keyLocator, "notificationSecret", NOTIFICATION_SECRET);
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
    void verify_validNotificationServiceToken_returnsIssuerName() {
        String token = token("notification-service", keyFor(NOTIFICATION_SECRET), "internal", 60_000);

        assertThat(authenticator.verify(token)).contains("notification-service");
    }

    @Test
    void verify_untrustedIssuer_returnsEmpty() {
        // Signed with a real HMAC key, but "gateway-service" was never registered as a trusted
        // caller - the locator has no key for it, so verification must fail closed.
        String token = token("gateway-service", keyFor(UNTRUSTED_SECRET), "internal", 60_000);

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
    void verify_forgedWithWrongKeyForClaimedIssuer_returnsEmpty() {
        // Claims to be core-banking (kid=core-banking) but is actually signed with
        // notification-service's secret - the locator picks core-banking's key for
        // verification, so the signature check fails.
        String token = token("core-banking", keyFor(NOTIFICATION_SECRET), "internal", 60_000);

        assertThat(authenticator.verify(token)).isEmpty();
    }

    @Test
    void verify_malformedToken_returnsEmpty() {
        assertThat(authenticator.verify("not-a-jwt")).isEmpty();
    }
}
