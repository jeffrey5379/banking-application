package com.bankapp.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Date;

// Backed by Redis, same shape as TokenBlacklist, but keyed by username instead of by token: a
// stateless JWT service has no inventory of which tokens are currently outstanding for a user

@Component
@RequiredArgsConstructor
public class AccountRevocationStore {

    private static final String KEY_PREFIX = "revoked-since:";

    private final StringRedisTemplate redisTemplate;

    @Value("${jwt.expiration}")
    private long maxTokenLifetimeMs;

    public void revokeAllTokensIssuedBefore(String username) {
        redisTemplate.opsForValue().set(KEY_PREFIX + username, String.valueOf(System.currentTimeMillis()),
                Duration.ofMillis(maxTokenLifetimeMs));
    }

    public boolean isRevoked(String username, Date tokenIssuedAt) {
        String revokedSinceMs = redisTemplate.opsForValue().get(KEY_PREFIX + username);
        if (revokedSinceMs == null) {
            return false;
        }
        return tokenIssuedAt.getTime() <= Long.parseLong(revokedSinceMs);
    }
}
