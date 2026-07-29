package com.bankapp.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Short-lived, single-use tickets that let the browser's native EventSource (which can't send an
 * Authorization header) prove who it is without putting a long-lived JWT in a URL - a URL query
 * string ends up in ALB/CloudFront access logs and browser history, a JWT valid for the rest of
 * the day should never sit there. A ticket is a random opaque token, redeemable exactly once,
 * scoped to a 15-second window - just long enough for the browser to open the SSE connection
 * right after fetching it.
 *
 * Backed by Redis (same shared instance every other service uses) rather than an in-memory map so
 * this still works if notification-service ever runs as more than one instance behind the ALB.
 */
@Component
@RequiredArgsConstructor
public class TicketService {

    private static final String KEY_PREFIX = "notif-ticket:";
    private static final Duration TTL = Duration.ofSeconds(15);

    private final ReactiveStringRedisTemplate redisTemplate;
    private final SecureRandom random = new SecureRandom();

    public Mono<String> issue(UUID userId) {
        String ticket = generateToken();
        return redisTemplate.opsForValue()
                .set(KEY_PREFIX + ticket, userId.toString(), TTL)
                .thenReturn(ticket);
    }

    // Atomic get-then-delete: two concurrent redemptions of the same ticket must not both succeed.
    public Mono<UUID> consume(String ticket) {
        return redisTemplate.opsForValue()
                .getAndDelete(KEY_PREFIX + ticket)
                .map(UUID::fromString);
    }

    private String generateToken() {
        byte[] bytes = new byte[24];
        random.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }
}
