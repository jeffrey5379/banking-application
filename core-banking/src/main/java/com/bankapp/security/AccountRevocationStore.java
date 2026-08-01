package com.bankapp.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Date;

// Read-only: only identity-service ever calls revokeAllTokensIssuedBefore()
// (see its own AccountRevocationStore) - core-banking just checks the same Redis-backed "revoked-since"
@Component
@RequiredArgsConstructor
public class AccountRevocationStore {

    private static final String KEY_PREFIX = "revoked-since:";

    private final StringRedisTemplate redisTemplate;

    public boolean isRevoked(String username, Date tokenIssuedAt) {
        String revokedSinceMs = redisTemplate.opsForValue().get(KEY_PREFIX + username);
        if (revokedSinceMs == null) {
            return false;
        }
        return tokenIssuedAt.getTime() <= Long.parseLong(revokedSinceMs);
    }
}
