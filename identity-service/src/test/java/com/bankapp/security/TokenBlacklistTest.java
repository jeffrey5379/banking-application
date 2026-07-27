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

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

// Redis is mocked here (fast, no real server needed for these) - the actual Redis interaction
// (TTL really expiring, hasKey really reflecting it) is covered by a live verification, not a
// unit test, since that's inherently about real Redis behavior rather than our own logic.
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TokenBlacklistTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private TokenBlacklist tokenBlacklist;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        tokenBlacklist = new TokenBlacklist(redisTemplate);
    }

    // ── isRevoked ─────────────────────────────────────────────────────────

    @Test
    void isRevoked_keyNotInRedis_returnsFalse() {
        when(redisTemplate.hasKey("blacklist:token:unknown-token")).thenReturn(false);
        assertThat(tokenBlacklist.isRevoked("unknown-token")).isFalse();
    }

    @Test
    void isRevoked_keyInRedis_returnsTrue() {
        when(redisTemplate.hasKey("blacklist:token:tok")).thenReturn(true);
        assertThat(tokenBlacklist.isRevoked("tok")).isTrue();
    }

    @Test
    void isRevoked_redisReturnsNull_returnsFalse() {
        // hasKey() is Boolean, not boolean - a null (e.g. from a degraded Redis response)
        // must not be treated as "revoked".
        when(redisTemplate.hasKey("blacklist:token:tok")).thenReturn(null);
        assertThat(tokenBlacklist.isRevoked("tok")).isFalse();
    }

    // ── revoke ────────────────────────────────────────────────────────────

    @Test
    void revoke_futureExpiry_storesKeyWithMatchingTtl() {
        long futureExpiry = System.currentTimeMillis() + 3_600_000L;

        tokenBlacklist.revoke("tok", futureExpiry);

        verify(valueOperations).set(eq("blacklist:token:tok"), eq("1"), argThat((Duration d) ->
                d.toMillis() > 3_500_000L && d.toMillis() <= 3_600_000L));
    }

    @Test
    void revoke_pastExpiry_storesNothing() {
        long pastExpiry = System.currentTimeMillis() - 1L;

        tokenBlacklist.revoke("tok", pastExpiry);

        verify(valueOperations, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void revoke_calledTwiceWithDifferentTokens_bothStored() {
        long futureExpiry = System.currentTimeMillis() + 3_600_000L;

        tokenBlacklist.revoke("tok-a", futureExpiry);
        tokenBlacklist.revoke("tok-b", futureExpiry);

        verify(valueOperations).set(eq("blacklist:token:tok-a"), eq("1"), any(Duration.class));
        verify(valueOperations).set(eq("blacklist:token:tok-b"), eq("1"), any(Duration.class));
    }

    @Test
    void revoke_pastThenFutureForSameToken_onlyFutureCallStores() {
        long pastExpiry = System.currentTimeMillis() - 1L;
        long futureExpiry = System.currentTimeMillis() + 3_600_000L;

        tokenBlacklist.revoke("tok", pastExpiry);
        tokenBlacklist.revoke("tok", futureExpiry);

        verify(valueOperations, times(1)).set(eq("blacklist:token:tok"), eq("1"), any(Duration.class));
    }
}
