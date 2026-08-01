package com.bankapp.service;

import com.bankapp.exception.IdempotencyInProgressException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Deduplicates retried mutating requests (transfer/exchange/createAccount) so that a client
 * retry after a lost response - e.g. the server committed the operation but the reply never
 * reached the browser - replays the original result instead of re-applying the operation.
 *
 * The client supplies an Idempotency-Key header per attempt and resends the same key on retry.
 * Keys are scoped per-user and per-operation so a collision across users/endpoints is not possible.
 * Only successful completions are cached; a thrown exception is never cached, so a legitimately
 * failed attempt (e.g. insufficient funds) can simply be retried.
 *
 * Backed by Redis instead of a local map + local lock: with multiple core-banking instances
 * behind a load balancer, two retries of the same key could land on different instances, and a
 * local-only lock would only serialize the two on the SAME instance - both could still execute
 * the (non-idempotent) underlying operation. The Redis lock key is a mutex shared across every
 * instance: whichever request acquires it runs the action, everyone else waits and replays its
 * result once it's written.
 */
@Component
public class IdempotencyStore {

    private static final String KEY_PREFIX = "idempotency:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final Duration resultTtl;
    private final Duration lockTtl;
    private final Duration pollInterval;
    private final Duration maxWait;

    @Autowired
    public IdempotencyStore(RedisTemplate<String, Object> redisTemplate) {
        this(redisTemplate, Duration.ofHours(24), Duration.ofSeconds(30), Duration.ofMillis(100), Duration.ofSeconds(10));
    }

    // Visible for tests that need a short maxWait/pollInterval to exercise the "still in
    // progress after waiting" path without a real 10-second wait.
    IdempotencyStore(RedisTemplate<String, Object> redisTemplate, Duration resultTtl, Duration lockTtl,
                     Duration pollInterval, Duration maxWait) {
        this.redisTemplate = redisTemplate;
        this.resultTtl = resultTtl;
        this.lockTtl = lockTtl;
        this.pollInterval = pollInterval;
        this.maxWait = maxWait;
    }

    @SuppressWarnings("unchecked")
    public <T> T execute(String idempotencyKey, String operation, Supplier<T> action) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return action.get();
        }

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        String baseKey = KEY_PREFIX + username + '|' + operation + '|' + idempotencyKey;
        String lockKey = baseKey + ":lock";
        String resultKey = baseKey + ":result";

        Object cached = redisTemplate.opsForValue().get(resultKey);
        if (cached != null) {
            return (T) cached;
        }

        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(lockKey, "1", lockTtl);
        if (Boolean.TRUE.equals(acquired)) {
            try {
                T result = action.get();
                redisTemplate.opsForValue().set(resultKey, result, resultTtl);
                return result;
            } finally {
                redisTemplate.delete(lockKey);
            }
        }

        return waitForResult(resultKey);
    }

    // Someone else holds the lock for this key - poll for the result they'll write rather than
    // risk a second, concurrent execution of a non-idempotent operation.
    @SuppressWarnings("unchecked")
    private <T> T waitForResult(String resultKey) {
        long deadline = System.currentTimeMillis() + maxWait.toMillis();
        while (System.currentTimeMillis() < deadline) {
            Object cached = redisTemplate.opsForValue().get(resultKey);
            if (cached != null) {
                return (T) cached;
            }
            try {
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IdempotencyInProgressException(
                "This request is already being processed, please retry shortly");
    }
}
