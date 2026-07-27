package com.bankapp.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

// Backed by Redis (not a local map) so a revoked token is honored by every identity-service
// instance, not just the one that handled the logout request. TTL is delegated to Redis itself
// (the key expires and simply disappears) instead of a manually-checked expiresAtMs field.
@Component
@RequiredArgsConstructor
public class TokenBlacklist {

    private static final String KEY_PREFIX = "blacklist:token:";

    private final StringRedisTemplate redisTemplate;

    public void revoke(String token, long expiryMs) {
        long ttlMs = expiryMs - System.currentTimeMillis();
        if (ttlMs <= 0) {
            return; // already expired - nothing meaningful to blacklist
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "1", Duration.ofMillis(ttlMs));
    }

    public boolean isRevoked(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
