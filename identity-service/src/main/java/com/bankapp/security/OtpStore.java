package com.bankapp.security;

import com.bankapp.exception.InvalidOtpException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

/**
 * Holds the OTP challenges issued during the two-step login (password verified, code sent,
 * awaiting the code back) until they're verified or expire. Each challenge is single-use and
 * capped on wrong-code attempts to make brute-forcing a 6-digit code impractical.
 *
 * Backed by Redis (hash per challenge token) instead of a local map, so a challenge created by
 * one identity-service instance can be verified against by another - required once there's more
 * than one instance behind a load balancer. Expiry is delegated to Redis's own TTL; the
 * attempt counter uses HINCRBY, which Redis executes atomically server-side, so concurrent
 * verify() calls for the same token can't both slip past the attempt cap.
 */
@Component
@RequiredArgsConstructor
public class OtpStore {

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;
    private static final String KEY_PREFIX = "otp:challenge:";

    private final StringRedisTemplate redisTemplate;

    public String createChallenge(String username, String code) {
        String token = UUID.randomUUID().toString();
        String key = KEY_PREFIX + token;
        redisTemplate.opsForHash().putAll(key, Map.of("username", username, "code", code, "attempts", "0"));
        redisTemplate.expire(key, TTL);
        return token;
    }

    /**
     * Verifies the code for a challenge and, on success, consumes it (single-use) and returns
     * the username it was issued for. Throws on a missing/expired challenge, too many wrong
     * attempts (which also consumes it), or a wrong code (which does not - the caller can retry
     * with the same token up to the attempt cap).
     */
    public String verify(String challengeToken, String code) {
        String key = KEY_PREFIX + challengeToken;

        Object storedCode = redisTemplate.opsForHash().get(key, "code");
        if (storedCode == null) {
            throw new InvalidOtpException("Code expired or invalid, please log in again");
        }

        Long attempts = redisTemplate.opsForHash().increment(key, "attempts", 1);
        if (attempts > MAX_ATTEMPTS) {
            redisTemplate.delete(key);
            throw new InvalidOtpException("Too many incorrect attempts, please log in again");
        }
        if (!storedCode.equals(code)) {
            throw new InvalidOtpException("Invalid code");
        }

        Object username = redisTemplate.opsForHash().get(key, "username");
        redisTemplate.delete(key);
        return (String) username;
    }
}
