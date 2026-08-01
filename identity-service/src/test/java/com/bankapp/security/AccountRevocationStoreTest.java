package com.bankapp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountRevocationStoreTest {

    private static final long JWT_EXPIRATION_MS = 86_400_000L;

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private AccountRevocationStore store;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        store = new AccountRevocationStore(redisTemplate);
        ReflectionTestUtils.setField(store, "maxTokenLifetimeMs", JWT_EXPIRATION_MS);
    }

    // ── isRevoked ─────────────────────────────────────────────────────────

    @Test
    void isRevoked_noMarkerStored_returnsFalse() {
        when(valueOperations.get("revoked-since:alice")).thenReturn(null);

        assertThat(store.isRevoked("alice", new Date())).isFalse();
    }

    @Test
    void isRevoked_tokenIssuedBeforeRevocation_returnsTrue() {
        long revokedAt = System.currentTimeMillis();
        when(valueOperations.get("revoked-since:alice")).thenReturn(String.valueOf(revokedAt));

        assertThat(store.isRevoked("alice", new Date(revokedAt - 1000))).isTrue();
    }

    @Test
    void isRevoked_tokenIssuedAtExactRevocationInstant_returnsTrue() {
        // <=, not < - a token minted in the very same millisecond as the revocation must not
        // slip through as "issued after".
        long revokedAt = System.currentTimeMillis();
        when(valueOperations.get("revoked-since:alice")).thenReturn(String.valueOf(revokedAt));

        assertThat(store.isRevoked("alice", new Date(revokedAt))).isTrue();
    }

    @Test
    void isRevoked_tokenIssuedAfterRevocation_returnsFalse() {
        long revokedAt = System.currentTimeMillis();
        when(valueOperations.get("revoked-since:alice")).thenReturn(String.valueOf(revokedAt));

        assertThat(store.isRevoked("alice", new Date(revokedAt + 1000))).isFalse();
    }

    @Test
    void isRevoked_differentUsername_notAffectedByAnotherUsersRevocation() {
        when(valueOperations.get("revoked-since:bob")).thenReturn(null);

        assertThat(store.isRevoked("bob", new Date())).isFalse();
    }

    // ── revokeAllTokensIssuedBefore ──────────────────────────────────────

    @Test
    void revokeAllTokensIssuedBefore_storesMarkerWithJwtExpirationTtl() {
        store.revokeAllTokensIssuedBefore("alice");

        verify(valueOperations).set(eq("revoked-since:alice"), any(String.class),
                eq(Duration.ofMillis(JWT_EXPIRATION_MS)));
    }
}
