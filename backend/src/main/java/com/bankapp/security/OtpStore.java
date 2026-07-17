package com.bankapp.security;

import com.bankapp.exception.InvalidOtpException;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Holds the OTP challenges issued during the two-step login (password verified, code sent,
 * awaiting the code back) until they're verified or expire. Each challenge is single-use and
 * capped on wrong-code attempts to make brute-forcing a 6-digit code impractical.
 */
@Component
public class OtpStore {

    private static final long DEFAULT_TTL_MS = TimeUnit.MINUTES.toMillis(5);
    private static final int MAX_ATTEMPTS = 5;

    private final long ttlMs;
    private final ConcurrentHashMap<String, Challenge> challenges = new ConcurrentHashMap<>();

    public OtpStore() {
        this(DEFAULT_TTL_MS);
    }

    OtpStore(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    private record Challenge(String username, String code, long expiresAtMs, AtomicInteger attempts) {
    }

    public String createChallenge(String username, String code) {
        String token = UUID.randomUUID().toString();
        challenges.put(token, new Challenge(username, code, System.currentTimeMillis() + ttlMs, new AtomicInteger(0)));
        return token;
    }

    /**
     * Verifies the code for a challenge and, on success, consumes it (single-use) and returns
     * the username it was issued for. Throws on a missing/expired challenge, too many wrong
     * attempts, or a wrong code - the challenge is discarded in all of those cases too, so a
     * failed attempt always requires the user to log in again from scratch.
     */
    public String verify(String challengeToken, String code) {
        Challenge challenge = challenges.get(challengeToken);
        if (challenge == null || challenge.expiresAtMs() < System.currentTimeMillis()) {
            challenges.remove(challengeToken);
            throw new InvalidOtpException("Code expired or invalid, please log in again");
        }
        if (challenge.attempts().incrementAndGet() > MAX_ATTEMPTS) {
            challenges.remove(challengeToken);
            throw new InvalidOtpException("Too many incorrect attempts, please log in again");
        }
        if (!challenge.code().equals(code)) {
            throw new InvalidOtpException("Invalid code");
        }
        challenges.remove(challengeToken);
        return challenge.username();
    }
}
