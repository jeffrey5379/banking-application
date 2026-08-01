package com.bankapp.notification.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;

import static org.assertj.core.api.Assertions.assertThat;

class InternalServiceTokenIssuerTest {

    private static final String SECRET = "fB2RHu5YPOXLoP7QaKL4ivQ0bmLUExu+LoZ/vMn5qqEY5j+W4tOAtyQCIq/+E3ol";

    private InternalServiceTokenIssuer issuer;

    @BeforeEach
    void setUp() {
        issuer = new InternalServiceTokenIssuer();
        ReflectionTestUtils.setField(issuer, "secret", SECRET);
        ReflectionTestUtils.invokeMethod(issuer, "init");
    }

    @Test
    void mintToken_hasNotificationServiceKidAndInternalScope() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));

        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(issuer.mintToken());

        assertThat(jws.getHeader().getKeyId()).isEqualTo("notification-service");
        assertThat(jws.getPayload().get("scope", String.class)).isEqualTo("internal");
    }

    @Test
    void mintToken_expiresWithinSixtySeconds() {
        SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET));

        Jws<Claims> jws = Jwts.parser().verifyWith(key).build().parseSignedClaims(issuer.mintToken());
        Claims claims = jws.getPayload();

        long ttlMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertThat(ttlMillis).isEqualTo(60_000L);
    }
}
