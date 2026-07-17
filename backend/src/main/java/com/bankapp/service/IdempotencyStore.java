package com.bankapp.service;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Deduplicates retried mutating requests (credit/debit/exchange/createAccount) so that a client
 * retry after a lost response - e.g. the server committed the operation but the reply never
 * reached the browser - replays the original result instead of re-applying the operation.
 *
 * The client supplies an Idempotency-Key header per attempt and resends the same key on retry.
 * Keys are scoped per-user and per-operation so a collision across users/endpoints is not possible.
 * Only successful completions are cached; a thrown exception is never cached, so a legitimately
 * failed attempt (e.g. insufficient funds) can simply be retried.
 */
@Component
public class IdempotencyStore {

    private static final long DEFAULT_TTL_MS = TimeUnit.HOURS.toMillis(24);

    private final long ttlMs;
    private final ConcurrentHashMap<String, CachedResult> results = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    public IdempotencyStore() {
        this(DEFAULT_TTL_MS);
    }

    IdempotencyStore(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    private record CachedResult(Object value, long expiresAtMs) {
    }

    @SuppressWarnings("unchecked")
    public <T> T execute(String idempotencyKey, String operation, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        String cacheKey = username + '|' + operation + '|' + idempotencyKey;

        // Per-key lock so concurrent requests carrying the same key serialize instead of both
        // executing the (non-idempotent) underlying operation.
        Object lock = locks.computeIfAbsent(cacheKey, k -> new Object());
        synchronized (lock) {
            CachedResult cached = results.get(cacheKey);
            if (cached != null && cached.expiresAtMs() > System.currentTimeMillis()) {
                return (T) cached.value();
            }
            T result = action.get();
            results.put(cacheKey, new CachedResult(result, System.currentTimeMillis() + ttlMs));
            return result;
        }
    }
}
