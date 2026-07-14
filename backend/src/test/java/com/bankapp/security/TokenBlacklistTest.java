package com.bankapp.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenBlacklistTest {

    private TokenBlacklist tokenBlacklist;

    @BeforeEach
    void setUp() {
        tokenBlacklist = new TokenBlacklist();
    }

    // ── isRevoked ─────────────────────────────────────────────────────────

    @Test
    void isRevoked_unknownToken_returnsFalse() {
        assertThat(tokenBlacklist.isRevoked("unknown-token")).isFalse();
    }

    @Test
    void isRevoked_revokedActiveToken_returnsTrue() {
        long futureExpiry = System.currentTimeMillis() + 3_600_000L;

        tokenBlacklist.revoke("tok", futureExpiry);

        assertThat(tokenBlacklist.isRevoked("tok")).isTrue();
    }

    @Test
    void isRevoked_expiredToken_returnsFalse() {
        long pastExpiry = System.currentTimeMillis() - 1L;

        tokenBlacklist.revoke("tok", pastExpiry);

        assertThat(tokenBlacklist.isRevoked("tok")).isFalse();
    }

    @Test
    void isRevoked_expiredToken_removesEntryFromMap() {
        long pastExpiry = System.currentTimeMillis() - 1L;
        tokenBlacklist.revoke("tok", pastExpiry);

        tokenBlacklist.isRevoked("tok");

        // After cleanup the token should no longer be found
        assertThat(tokenBlacklist.isRevoked("tok")).isFalse();
    }

    // ── revoke ────────────────────────────────────────────────────────────

    @Test
    void revoke_calledTwiceWithDifferentTokens_bothRevoked() {
        long futureExpiry = System.currentTimeMillis() + 3_600_000L;

        tokenBlacklist.revoke("tok-a", futureExpiry);
        tokenBlacklist.revoke("tok-b", futureExpiry);

        assertThat(tokenBlacklist.isRevoked("tok-a")).isTrue();
        assertThat(tokenBlacklist.isRevoked("tok-b")).isTrue();
    }

    @Test
    void revoke_updatesExpiryWhenCalledTwiceForSameToken() {
        long pastExpiry = System.currentTimeMillis() - 1L;
        long futureExpiry = System.currentTimeMillis() + 3_600_000L;

        tokenBlacklist.revoke("tok", pastExpiry);
        tokenBlacklist.revoke("tok", futureExpiry);

        assertThat(tokenBlacklist.isRevoked("tok")).isTrue();
    }
}
