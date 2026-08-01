package com.bankapp.notification.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Date;

@Component
@RequiredArgsConstructor
public class AccountRevocationStore {

    private static final String KEY_PREFIX = "revoked-since:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public Mono<Boolean> isRevoked(String username, Date tokenIssuedAt) {
        return redisTemplate.opsForValue().get(KEY_PREFIX + username)
                .map(revokedSinceMs -> tokenIssuedAt.getTime() <= Long.parseLong(revokedSinceMs))
                .defaultIfEmpty(false);
    }
}
